package com.capsule.dialogue.service;

import static com.capsule.dialogue.domain.ChatMessage.CHARACTER;
import static com.capsule.dialogue.domain.ChatMessage.USER;

import com.capsule.common.error.ApiException;
import com.capsule.common.ratelimit.RateLimiter;
import com.capsule.content.api.CharacterQuery;
import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.content.api.ContentTypes.Reaction;
import com.capsule.content.api.ReactionQuery;
import com.capsule.dialogue.client.LlmJson;
import com.capsule.dialogue.config.DialogueProperties;
import com.capsule.dialogue.domain.ChatMessage;
import com.capsule.dialogue.domain.Conversation;
import com.capsule.dialogue.domain.ConversationRecap;
import com.capsule.dialogue.domain.DialogueStateEntity;
import com.capsule.dialogue.dto.ChatInput;
import com.capsule.dialogue.dto.ChatLine;
import com.capsule.dialogue.dto.ChatOutcome;
import com.capsule.dialogue.dto.EngineOptions;
import com.capsule.dialogue.dto.LlmContext;
import com.capsule.dialogue.dto.MessageRequest;
import com.capsule.dialogue.dto.MessageResponse;
import com.capsule.dialogue.dto.ReactionCharacter;
import com.capsule.dialogue.dto.ReactionLine;
import com.capsule.dialogue.dto.ReactionResponse;
import com.capsule.dialogue.engine.DialogueState;
import com.capsule.dialogue.repository.ChatMessageRepository;
import com.capsule.dialogue.repository.ConversationRecapRepository;
import com.capsule.dialogue.repository.ConversationRepository;
import com.capsule.dialogue.repository.DialogueStateRepository;
import com.capsule.relation.api.RelationCommands;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.api.RelationTypes.AffinityChange;
import com.capsule.relation.api.RelationTypes.ChatScore;
import com.capsule.relation.api.RelationTypes.RelationView;
import com.capsule.relation.api.RelationTypes.SignalType;
import com.capsule.session.api.SessionClosingHandler;
import com.capsule.session.api.SessionQuery;
import com.capsule.session.api.SessionTypes.SessionClosing;
import com.capsule.session.api.SessionTypes.SessionEventInfo;
import com.capsule.session.api.SessionTypes.SessionInfo;
import com.capsule.user.api.MemoryQuery;
import com.capsule.user.api.UserQuery;
import com.capsule.user.api.UserTypes.MemoryInfo;
import com.capsule.user.api.UserTypes.UserProfile;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상태 반응(api.md 19), 메시지(20), 공부 종료 때 대화 마무리(22, session이 부름).
 * 여기서는 API 규칙(검증·재전송·저장·5초 대체 대사)만 지키고, LLM이 쓰는 글은 ReactionWriter · ChatPipeline · ClosingWriter가 맡는다.
 * 메시지의 LLM 호출은 DB 트랜잭션 밖에서 한다: 읽기 → LLM → 저장(한 트랜잭션).
 */
@Service
public class DialogueService {

    private static final Logger log = LoggerFactory.getLogger(DialogueService.class);

    /** 명세 19·20·22: 5초 안에 못 만들면 고정 대사 */
    private static final Duration LLM_BUDGET = Duration.ofSeconds(5);
    /** 같은 requestId가 처리 중이면 기다리는 한도 (LLM 5초 + 저장 여유) */
    private static final Duration IN_FLIGHT_WAIT = Duration.ofSeconds(8);

    private static final ExecutorService LLM_THREADS = Executors.newVirtualThreadPerTaskExecutor();
    private static final int MESSAGES_PER_MINUTE = 10;
    // TODO(judgment): J-14 '수다가 길어졌다'의 기준 (승희 2026-11-05 임시: 최근 10분에 사용자 말 8번, 이번 말 포함)
    static final int CHATTY_MESSAGES = 8;
    static final Duration CHATTY_WINDOW = Duration.ofMinutes(10);
    // TODO(judgment): J-15 '공부 포기'의 기준 (승희 2026-11-05 임시: 직접 끝냈는데 목표 시간의 절반 미만)
    static final double GIVE_UP_RATIO = 0.5;
    private static final ZoneId DAY_ZONE = ZoneId.of("Asia/Seoul");
    /** 세션 카드 머리말 날짜 (예: "11/03 52분") */
    private static final DateTimeFormatter CARD_DATE = DateTimeFormatter.ofPattern("MM/dd");

    private final SessionQuery sessions;
    private final CharacterQuery characters;
    private final ReactionQuery reactions;
    private final RelationQuery relationQuery;
    private final RelationCommands relationCommands;
    private final MemoryQuery memories;
    private final UserQuery users;
    private final RateLimiter rateLimiter;
    private final ReactionWriter reactionWriter;
    private final FixedLines fixedLines;
    private final ChatPipeline chat;
    private final ChatReplier replier;
    private final ChatScorer scorer;
    private final CharacterContents characterContents;
    private final MemoryKeeper memoryKeeper;
    private final ClosingWriter closingWriter;
    private final ConversationRepository conversations;
    private final ConversationRecapRepository recapRepository;
    private final ChatMessageRepository messages;
    private final DialogueStateRepository states;
    private final TransactionTemplate tx;
    private final DialogueProperties props;
    private final Clock clock;

    DialogueService(
            SessionQuery sessions,
            CharacterQuery characters,
            ReactionQuery reactions,
            RelationQuery relationQuery,
            RelationCommands relationCommands,
            MemoryQuery memories,
            UserQuery users,
            RateLimiter rateLimiter,
            ReactionWriter reactionWriter,
            FixedLines fixedLines,
            List<ChatPipeline> pipelines,
            ChatReplier replier,
            ChatScorer scorer,
            CharacterContents characterContents,
            MemoryKeeper memoryKeeper,
            ClosingWriter closingWriter,
            ConversationRepository conversations,
            ConversationRecapRepository recapRepository,
            ChatMessageRepository messages,
            DialogueStateRepository states,
            PlatformTransactionManager transactionManager,
            DialogueProperties props,
            Clock clock) {
        this.sessions = sessions;
        this.characters = characters;
        this.reactions = reactions;
        this.relationQuery = relationQuery;
        this.relationCommands = relationCommands;
        this.memories = memories;
        this.users = users;
        this.rateLimiter = rateLimiter;
        this.reactionWriter = reactionWriter;
        this.fixedLines = fixedLines;
        this.chat = pipelines.stream()
                .filter(p -> p.name().equals(props.chatPipeline()))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException("capsule.dialogue.chat-pipeline을 모름: " + props.chatPipeline()));
        this.replier = replier;
        this.scorer = scorer;
        this.characterContents = characterContents;
        this.memoryKeeper = memoryKeeper;
        this.closingWriter = closingWriter;
        this.conversations = conversations;
        this.recapRepository = recapRepository;
        this.messages = messages;
        this.states = states;
        this.tx = new TransactionTemplate(transactionManager);
        this.props = props;
        this.clock = clock;
    }

    // --- 19 상태 반응 ---

    public ReactionResponse react(
            long userId, long sessionId, long eventId, UUID requestId, Integer intensity, Boolean tts) {
        SessionInfo session = openSession(userId, sessionId);
        SessionEventInfo event = sessions.findEvent(sessionId, eventId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND"));
        long characterId = session.characterId();
        String trigger = triggerOf(event);
        Integer awayLevel = trigger.equals("AWAY") ? (intensity == null ? 1 : intensity) : null;
        if (awayLevel != null && (awayLevel < 1 || awayLevel > 3)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        }

        // 호감도는 상태 변화 전송(18)이 반영하고 그 응답으로 돌려준다. 여기서는 관계 단계(호감도 구간)만 읽어 말투를 고른다
        int relationLevel = relationQuery.view(userId, characterId).stage();
        // 상태 변화는 LLM이 대사 + 표정 (ADR 0022 결정 18), 동작 이벤트는 미리 쓴 대사(캐릭터 설정, 순서대로 → 없으면 공통 고정 대사).
        // LLM이 5초를 넘기거나 실패해도 미리 쓴 대사로
        ReactionLine line = null;
        boolean fallback = false;
        if (reactionWriter.usesLlm(trigger)) {
            // 상태 변화는 LLM: 관계 단계 + 지난 세션 흐름 + 이번 세션 최근 대화를 같이 넣는다
            CharacterInfo character = character(characterId);
            String nickname = nickname(userId);
            Optional<Conversation> conv = conversations.findByUserIdAndCharacterId(userId, characterId);
            String summary = conv.map(c -> recaps(c.id())).orElse(null);
            List<ChatLine> recent = conv.map(c -> tail(
                            lines(messages.findByConversationIdAndSessionIdOrderById(c.id(), sessionId)),
                            props.reactionContextLines()))
                    .orElse(List.of());
            LlmContext ctx = LlmContext.app(userId, sessionId, deadline());
            Optional<ReactionLine> generated = withinBudget(() -> reactionWriter.generate(
                    character, trigger, awayLevel, relationLevel, nickname, summary, recent, Map.of(), ctx));
            fallback = generated.isEmpty();
            line = generated.orElse(null);
        }
        if (line == null) {
            String situation = awayLevel != null && awayLevel > 1 ? trigger + "_" + awayLevel : trigger;
            line = characterContents
                    .next(characterId, situation, trigger, relationLevel, String.valueOf(sessionId), nickname(userId))
                    .orElseGet(() -> fixedLines.reaction(characterId, trigger, awayLevel, relationLevel, Map.of()));
            // TODO(judgment): J-12 ② LLM이 실패한 대체 대사의 감정. 지금은 없음(동작 없음), 추천은 그 대사 칸의 표정
            if (fallback) line = new ReactionLine(line.content(), null);
        }
        // 명세 19: 모션·표정은 대사의 감정으로 고른다 (trigger 기준 '알아채는' 동작은 18이 줌)
        // TODO(judgment): J-04 ④ 강도를 동작에도 쓸지. 지금은 대사(고정 대사 칸 · LLM 입력)에만
        // TODO(judgment): J-07 ①·J-12 ① 감정 후보를 어디서 정할지. 반응 표 목록을 LLM에 주면 content.api 계약 필요(#9, #33),
        //   그 목록에서 trigger 이름(GREETING 등)은 빼야 함(같은 컬럼에 있음). 지금은 설정 capsule.dialogue.emotions
        Reaction reaction = emotionReaction(characterId, line.emotion());
        // 뼈대는 세션당 캐릭터 1명
        return new ReactionResponse(
                requestId,
                List.of(new ReactionCharacter(
                        characterId,
                        line.content(),
                        line.emotion(),
                        reaction.motion(),
                        reaction.expression(),
                        fallback,
                        audio(tts))));
    }

    // --- 20 메시지 ---

    public MessageResponse send(long userId, long sessionId, MessageRequest request) {
        SessionInfo session = openSession(userId, sessionId);
        if (request.characterId() != session.characterId()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CHARACTER_NOT_IN_SESSION");
        }
        Conversation conversation = conversation(userId, session.characterId());

        // 재전송은 한도 검사 전에 돌려준다: 응답을 못 받아 다시 보낸 요청이 429가 되면 안 됨
        Optional<MessageResponse> answered = answered(conversation, session, request);
        if (answered.isPresent()) {
            return answered.get();
        }
        Optional<ChatMessage> asked = question(conversation, request);
        if (asked.isPresent()) {
            return awaitAnswer(session, conversation, request, asked.get().createdAt());
        }
        // 한도 검사보다 먼저 저장한다: 같은 requestId가 동시에 오면 늦은 쪽은 UNIQUE에 막혀 먼저 온 쪽의 답장을 기다린다.
        // 그래서 처리 중 재전송은 횟수에 세지 않는다(명세 20). 저장은 바로 커밋되므로 429면 이 사용자 말을 지운다
        try {
            messages.saveAndFlush(new ChatMessage(
                    conversation.id(), sessionId, request.requestId(), USER, request.content(), Instant.now(clock)));
        } catch (DataIntegrityViolationException e) {
            // 다른 쪽이 먼저 저장했으면 그 답을 기다린다. 다른 이유의 무결성 오류면 그대로 던진다
            ChatMessage other = question(conversation, request).orElseThrow(() -> e);
            return awaitAnswer(session, conversation, request, other.createdAt());
        }
        if (!rateLimiter.tryAcquire("rate:chat:" + userId, MESSAGES_PER_MINUTE, Duration.ofMinutes(1))) {
            messages.deleteQuestion(conversation.id(), request.requestId());
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS");
        }
        return answer(session, conversation, request);
    }

    /**
     * 읽기 → LLM(트랜잭션 밖, 5초) → 저장(한 트랜잭션: 호감도 · 답장 · 엔진 상태).
     * 저장 트랜잭션은 호감도(applyChat)부터 부른다: applyChat은 relation 행을 잠그고, 공부 종료(22)도 정산 때 같은 행을 잠근 채
     * 커밋까지 간다. 그래서 종료와 겹치면 여기서 종료가 끝나길 기다린 뒤 아래 세션 확인에서 끝난 것을 보고 아무것도 남기지 않는다.
     * 종료보다 먼저 잡으면 이 답장까지 커밋된 뒤에 종료가 정산·요약한다. 잠금 순서가 종료(세션 → relation → 대화)와 같아 교착이 없다.
     */
    private MessageResponse answer(SessionInfo session, Conversation conversation, MessageRequest request) {
        long userId = session.userId();
        long sessionId = session.id();
        long characterId = session.characterId();
        UUID requestId = request.requestId();

        List<ChatMessage> earlier =
                messages.findByConversationIdAndSessionIdOrderById(conversation.id(), sessionId).stream()
                        .filter(m -> !m.requestId().equals(requestId))
                        .toList();
        List<ChatLine> history = lines(earlier);
        Optional<DialogueStateEntity> saved = ChatPipeline.ENGINE.equals(chat.name())
                ? states.findBySessionIdAndCharacterId(sessionId, characterId)
                : Optional.empty();
        CharacterInfo character = character(characterId);
        ChatInput input = new ChatInput(
                character,
                nickname(userId),
                relationQuery.view(userId, characterId).stage(),
                recaps(conversation.id()),
                memoryKeeper.facts(userId, characterId),
                memoryLines(userId),
                history,
                request.content(),
                saved.map(DialogueService::engineState).orElse(null),
                Map.of(),
                EngineOptions.DEFAULT,
                sessions.onBreak(sessionId),
                chatty(earlier),
                (int) Duration.between(session.startedAt(), Instant.now(clock)).toMinutes());
        LlmContext ctx = LlmContext.app(userId, sessionId, deadline());
        Optional<ChatOutcome> outcome = withinBudget(() -> replier.reply(chat, input, ctx));
        String reply = outcome.map(ChatOutcome::reply)
                .orElseGet(() -> characterContents
                        .next(characterId, "REPLY_FALLBACK", null, input.relationLevel(), String.valueOf(sessionId), "")
                        .map(ReactionLine::content)
                        .orElseGet(() -> fixedLines.replyFallback(characterId)));
        // 명세 20: fallback이면 호감도 변화 없음 (0점이어도 applyChatScore를 불러 잠금 순서를 지킨다)
        SignalType signal = outcome.map(ChatOutcome::signal).orElse(null);
        ChatScore score = signal == null
                ? ChatScore.none()
                : scorer.score(
                        characterId,
                        signal,
                        earlier.stream()
                                .filter(m -> USER.equals(m.sender()) && m.signal() != null)
                                .map(m -> SignalType.valueOf(m.signal()))
                                .toList());

        MessageResponse response;
        try {
            response = tx.execute(status -> {
                AffinityChange affinity =
                        relationCommands.applyChatScore(userId, characterId, sessionId, requestId, score);
                if (sessions.find(sessionId).filter(SessionInfo::inProgress).isEmpty()) {
                    status.setRollbackOnly(); // 답하는 사이 공부가 끝났다: 정산·요약이 끝난 세션에 남기지 않는다
                    return null;
                }
                if (signal != null) messages.markSignal(conversation.id(), requestId, signal.name());
                messages.save(
                        new ChatMessage(conversation.id(), sessionId, requestId, CHARACTER, reply, Instant.now(clock)));
                outcome.map(ChatOutcome::engineState).ifPresent(st -> saveState(sessionId, characterId, st));
                // TODO(judgment): J-07 엔진의 표정 태그 → 반응 표 감정 이름. 지금 ENGINE은 감정을 안 정함(null → 동작 없음)
                String emotion = outcome.map(ChatOutcome::emotion).orElse(null);
                Reaction reaction = emotionReaction(characterId, emotion);
                return new MessageResponse(
                        requestId,
                        characterId,
                        reply,
                        emotion,
                        reaction.motion(),
                        reaction.expression(),
                        affinity,
                        outcome.isEmpty(),
                        audio(request.tts()));
            });
        } catch (DataIntegrityViolationException e) {
            // 기다리다 이어받은 요청과 먼저 온 요청이 둘 다 답했다: 먼저 저장된 답장으로
            return answered(conversation, session, request).orElseThrow(() -> e);
        }
        if (response == null) {
            // 종료가 이번 사용자 말을 요약 전에 읽지 못했으면 끝난 세션에 남는다. 요약에 들어갔으면 이미 지워져 있다
            messages.deleteQuestion(conversation.id(), requestId);
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_ENDED");
        }
        // 기억 ①: 답장을 저장한 뒤 이번 사용자 말에서 기억할 것을 정리한다 (답장을 늦추지 않게 따로, 마감 없음)
        Runnable remember = () -> memoryKeeper.afterTurn(
                character,
                userId,
                requestId,
                request.content(),
                tail(history, ChatReplier.CONTEXT_LINES),
                LlmContext.app(userId, sessionId, null));
        if (!props.memoryEnabled()) return response;
        if (props.memoryAsync()) LLM_THREADS.execute(remember);
        else remember.run();
        return response;
    }

    /**
     * 같은 requestId가 아직 처리 중: 끝날 때까지 기다렸다가 같은 답장 (명세 20).
     * 먼저 온 요청이 사용자 말을 저장한 지 IN_FLIGHT_WAIT가 지나도 답이 없으면(중간에 죽음) 이 요청이 이어서 답한다.
     */
    private MessageResponse awaitAnswer(
            SessionInfo session, Conversation conversation, MessageRequest request, Instant askedAt) {
        Instant deadline = askedAt.plus(IN_FLIGHT_WAIT);
        while (Instant.now(clock).isBefore(deadline)) {
            Optional<MessageResponse> answered = answered(conversation, session, request);
            if (answered.isPresent()) {
                return answered.get();
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        return answer(session, conversation, request);
    }

    /** 명세 19·20: 감정 이름으로 반응 표에서 모션·표정을 고른다. 감정이 없거나 그 감정 행이 없으면 동작 없음 */
    // TODO(judgment): J-07 ②·J-12 ② 감정이 없을 때(LLM 5초 초과 등) 기본 감정을 쓸지. 지금은 동작 없음
    private Reaction emotionReaction(long characterId, String emotion) {
        if (emotion == null) return Reaction.NONE;
        Reaction r = reactions.pick(characterId, emotion, null);
        if (!Reaction.NONE.equals(r)) return r;
        // 아직 에셋이 없는 표정이면 캐릭터 설정의 대체 표정으로 (PM: 화남 → 찡그림 등)
        String substitute = characterContents
                .find(characterId)
                .map(c -> c.fallbackExpression(emotion))
                .orElse(emotion);
        return substitute.equals(emotion) ? r : reactions.pick(characterId, substitute, null);
    }

    /** 이미 답한 requestId면 저장된 답장 */
    private Optional<MessageResponse> answered(Conversation conversation, SessionInfo session, MessageRequest request) {
        // ponytail: 재전송 때 원래 delta(와 감정·모션·표정·fallback)는 다시 보여주지 않음 (명세 20). 필요하면 chat_messages에 같이 저장
        return messages.findByConversationIdAndRequestIdAndSender(conversation.id(), request.requestId(), CHARACTER)
                .map(m -> new MessageResponse(
                        request.requestId(),
                        session.characterId(),
                        m.content(),
                        null,
                        null,
                        null,
                        unchanged(session.userId(), session.characterId()),
                        false,
                        audio(request.tts())));
    }

    // --- 22 공부 종료 ---

    /** 공부 종료 초안. pendingIds = 카드에 넣은 메시지 (저장할 때 지운다) */
    public record ClosingDraft(List<Long> pendingIds, Optional<String> card, String line)
            implements SessionClosingHandler.Draft {}

    /**
     * 공부 종료 1단계: 트랜잭션 밖에서 이번 세션 대화를 흐름 카드 한 장으로 쓰고 완료 대사를 만든다 (기억 ②). DB에 쓰지 않는다:
     * LLM을 기다리는 동안 DB 연결을 잡지 않게 (개발 서버 풀 2).
     * 대화방에 남은 메시지를 전부 쓴다: 카드에 넣은 메시지는 지우므로, 남은 게 있으면 앞선 종료에서 카드 쓰기가 실패한 것이다.
     * LLM이 실패하거나 5초를 넘겨도 공부 종료는 성공해야 하므로 고정 문구로 대신한다. 카드와 완료 대사는 같이 보낸다(둘 다 5초 안).
     */
    public ClosingDraft draft(SessionClosing closing) {
        List<ChatMessage> pending = conversations
                .findByUserIdAndCharacterId(closing.userId(), closing.characterId())
                .map(c -> messages.findByConversationIdOrderById(c.id()))
                .orElse(List.of());
        CharacterInfo character = character(closing.characterId());
        String nickname = nickname(closing.userId());
        LlmContext ctx = LlmContext.app(closing.userId(), closing.sessionId(), deadline());
        String header =
                LocalDate.now(clock.withZone(DAY_ZONE)).format(CARD_DATE) + " " + closing.studySeconds() / 60 + "분";

        CompletableFuture<String> recap = pending.isEmpty()
                ? null
                : start(() -> closingWriter.recap(
                        character,
                        nickname,
                        header,
                        memoryKeeper.facts(closing.userId(), closing.characterId()),
                        lines(pending),
                        Map.of(),
                        ctx));
        // 캐릭터 설정에 완료 대사가 있으면 그것 (PM: 말포이는 고정 문장, 관계 단계별), 없으면 LLM.
        // 목표 시간의 절반도 못 채우고 직접 끝냈으면 공부 포기 대사(GIVE_UP, 없으면 완료 대사)
        int stage = relationQuery.view(closing.userId(), closing.characterId()).stage();
        Optional<String> scriptedClosing = characterContents
                .next(
                        closing.characterId(),
                        gaveUp(closing) ? "GIVE_UP" : "CLOSING",
                        "CLOSING",
                        stage,
                        String.valueOf(closing.sessionId()),
                        nickname)
                .map(ReactionLine::content);
        CompletableFuture<String> line = scriptedClosing.isPresent()
                ? CompletableFuture.completedFuture(scriptedClosing.get())
                : start(() -> closingWriter.closingLine(
                        character, nickname, closing.studySeconds(), closing.endType(), Map.of(), ctx));

        Optional<String> card = recap == null ? Optional.empty() : await(recap);
        String text = await(line).orElseGet(() -> fixedLines.closingFallback(closing.characterId()));
        return new ClosingDraft(pending.stream().map(ChatMessage::id).toList(), card, text);
    }

    /** 공부 종료 2단계: 공부 종료 트랜잭션 안에서 초안을 저장하고 완료 대사를 돌려준다. draft가 null이면 여기서 만든다 (자동 마감 배치) */
    @Transactional
    public String close(SessionClosing closing, ClosingDraft draft) {
        ClosingDraft d = draft != null ? draft : draft(closing);
        if (d.card().isPresent()) {
            Conversation conversation = conversation(closing.userId(), closing.characterId());
            recapRepository.save(new ConversationRecap(
                    conversation.id(), closing.sessionId(), d.card().get(), Instant.now(clock)));
            // 최근 recap-max장만 남긴다. 오래된 흐름은 지워져도 된다 (오래 남길 사실은 기억 ①에 있음)
            List<ConversationRecap> all = recapRepository.findByConversationIdOrderByIdDesc(conversation.id());
            if (all.size() > props.recapMax()) recapRepository.deleteAll(all.subList(props.recapMax(), all.size()));
            messages.deleteAllByIdInBatch(d.pendingIds());
        }
        // 카드 쓰기에 실패하면 메시지를 남긴다: 다음 종료 때 그 세션 카드에 같이 들어간다
        states.deleteBySession(closing.sessionId());
        return d.line();
    }

    // --- 공통 ---

    /** 세션 첫 FOCUS는 GREETING, 그 밖의 FOCUS는 BREAK_END. 나머지(AWAY · RETURN · BREAK · 동작 이벤트)는 이벤트 타입 그대로 */
    private static String triggerOf(SessionEventInfo event) {
        if (!event.type().equals("FOCUS")) {
            return event.type();
        }
        return event.firstFocusOfSession() ? "GREETING" : "BREAK_END";
    }

    private SessionInfo openSession(long userId, long sessionId) {
        SessionInfo session = sessions.find(sessionId)
                .filter(s -> s.userId() == userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND"));
        if (!session.inProgress()) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_ENDED");
        }
        return session;
    }

    private CharacterInfo character(long characterId) {
        return characters.find(characterId).orElseThrow();
    }

    private String nickname(long userId) {
        return users.find(userId).map(UserProfile::nickname).orElse("");
    }

    /** 처음 대화면 만든다. 같은 사용자의 첫 대화가 동시에 와도 ON CONFLICT로 한 행만 */
    private Conversation conversation(long userId, long characterId) {
        return conversations.findByUserIdAndCharacterId(userId, characterId).orElseGet(() -> {
            conversations.insertIfAbsent(userId, characterId, Instant.now(clock));
            return conversations.findByUserIdAndCharacterId(userId, characterId).orElseThrow();
        });
    }

    private AffinityChange unchanged(long userId, long characterId) {
        RelationView view = relationQuery.view(userId, characterId);
        return new AffinityChange(0, view.points(), view.level(), false, view.stage(), view.stageName());
    }

    /** 지난 세션 흐름 카드를 오래된 것부터 줄마다. 없으면 null */
    private String recaps(long conversationId) {
        List<String> cards = recapRepository.findByConversationIdOrderByIdDesc(conversationId).stream()
                .limit(props.recapMax())
                .map(ConversationRecap::content)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        if (cards.isEmpty()) return null;
        java.util.Collections.reverse(cards);
        return String.join("\n", cards);
    }

    private List<String> memoryLines(long userId) {
        return memories.memoriesForConversation(userId).stream()
                .map(DialogueService::memoryLine)
                .toList();
    }

    /** 예: "시험: 중간고사 (2026-11-20 14:00)" */
    private static String memoryLine(MemoryInfo m) {
        String type = switch (m.type()) {
            case "EXAM" -> "시험";
            case "GOAL" -> "목표";
            case "PROMISE" -> "약속";
            default -> m.type();
        };
        String due =
                m.dueDate() == null ? "" : " (" + m.dueDate() + (m.dueTime() == null ? "" : " " + m.dueTime()) + ")";
        return type + ": " + m.title() + due;
    }

    private static List<ChatLine> tail(List<ChatLine> lines, int n) {
        return lines.subList(Math.max(0, lines.size() - n), lines.size());
    }

    /** 직접 끝냈는데 목표 시간의 절반도 못 채웠으면 공부 포기 (자동 종료는 포기로 보지 않는다) */
    static boolean gaveUp(SessionClosing closing) {
        Integer target = closing.targetMinutes();
        return "NORMAL".equals(closing.endType())
                && target != null
                && target > 0
                && closing.studySeconds() < target * 60 * GIVE_UP_RATIO;
    }

    private static List<ChatLine> lines(List<ChatMessage> chat) {
        return chat.stream().map(m -> new ChatLine(m.sender(), m.content())).toList();
    }

    /**
     * 최근 10분에 사용자 말이 이번 말까지 8번 이상이면 수다가 길어진 것. 공부 중일 때만 쓰인다(파이프라인이 거름).
     * ponytail: 쉬는 시간에 한 말도 센다. 쉬는 시간 직후 첫 말에 한 번 더 돌려보낼 수 있다. 문제가 되면 마지막 상태 이벤트 뒤의 말만 센다
     */
    private boolean chatty(List<ChatMessage> earlier) {
        Instant since = Instant.now(clock).minus(CHATTY_WINDOW);
        long recent = earlier.stream()
                .filter(m -> USER.equals(m.sender()) && m.createdAt().isAfter(since))
                .count();
        return recent + 1 >= CHATTY_MESSAGES;
    }

    /** 저장된 엔진 상태. 형식 버전이 다르거나 못 읽으면 null(새로 시작) */
    private static DialogueState engineState(DialogueStateEntity saved) {
        if (saved.engineVersion() != EngineChatPipeline.STATE_VERSION) {
            return null;
        }
        try {
            return LlmJson.read(saved.state(), DialogueState.class);
        } catch (RuntimeException e) {
            log.warn("대화 엔진 상태를 못 읽어 새로 시작", e);
            return null;
        }
    }

    /**
     * 답장 저장 트랜잭션 안에서 부른다 (relation 잠금 아래라 같은 세션의 상태 쓰기는 한 줄로 선다). 행을 새로 읽어 덮어쓴다.
     * ponytail: 같은 세션 메시지가 겹치면 둘 다 같은 옛 상태에서 턴을 만들었으므로 늦은 쪽이 앞 턴을 덮는다(엔진 기억 한 턴 손실).
     *   잦아지면 상태 버전을 비교해 늦은 쪽을 다시 돌린다
     */
    private void saveState(long sessionId, long characterId, DialogueState st) {
        String json = LlmJson.write(st);
        Instant now = Instant.now(clock);
        DialogueStateEntity row = states.findBySessionIdAndCharacterId(sessionId, characterId)
                .orElseGet(() ->
                        new DialogueStateEntity(sessionId, characterId, EngineChatPipeline.STATE_VERSION, json, now));
        row.update(EngineChatPipeline.STATE_VERSION, json, now);
        states.save(row);
    }

    private Optional<ChatMessage> question(Conversation conversation, MessageRequest request) {
        return messages.findByConversationIdAndRequestIdAndSender(conversation.id(), request.requestId(), USER);
    }

    /** 서비스 LLM 호출의 마감: 이 시각이 지나면 게이트웨이가 새 호출을 시작하지 않는다 (결과가 버려지므로) */
    private Instant deadline() {
        return Instant.now(clock).plus(LLM_BUDGET);
    }

    private static <T> CompletableFuture<T> start(Supplier<T> call) {
        return CompletableFuture.supplyAsync(call, LLM_THREADS).orTimeout(LLM_BUDGET.toSeconds(), TimeUnit.SECONDS);
    }

    /** 5초 안에 끝나지 않거나 실패하면 빈 값 → 호출한 쪽이 고정 대사를 쓴다 */
    private static <T> Optional<T> await(CompletableFuture<T> call) {
        try {
            return Optional.ofNullable(call.join());
        } catch (CompletionException e) {
            log.warn("LLM 호출 실패, 고정 대사로 대체", e.getCause());
            return Optional.empty();
        }
    }

    private static <T> Optional<T> withinBudget(Supplier<T> call) {
        return await(start(call));
    }

    // TODO(judgment): J-10 TTS 대사 음성(mp3) 생성. 지금은 tts:true여도 null
    private static String audio(Boolean tts) {
        return null;
    }
}
