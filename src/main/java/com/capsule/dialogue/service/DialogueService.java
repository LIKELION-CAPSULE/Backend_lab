package com.capsule.dialogue.service;

import static com.capsule.dialogue.domain.ChatMessage.CHARACTER;
import static com.capsule.dialogue.domain.ChatMessage.USER;

import com.capsule.common.error.ApiException;
import com.capsule.common.error.CommonErrorCode;
import com.capsule.common.ratelimit.RateLimiter;
import com.capsule.common.settings.RuntimeSettings;
import com.capsule.common.time.ClockConfig;
import com.capsule.content.api.CharacterQuery;
import com.capsule.content.api.ContentTypes.BackgroundInfo;
import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.content.api.ContentTypes.Reaction;
import com.capsule.content.api.ReactionQuery;
import com.capsule.credit.api.CreditCommands;
import com.capsule.credit.api.CreditQuery;
import com.capsule.credit.api.CreditTypes.Credits;
import com.capsule.credit.api.CreditTypes.SpendResult;
import com.capsule.dialogue.config.DialogueProperties;
import com.capsule.dialogue.domain.ChatMessage;
import com.capsule.dialogue.domain.Conversation;
import com.capsule.dialogue.domain.ConversationRecap;
import com.capsule.dialogue.domain.DialogueErrorCode;
import com.capsule.dialogue.dto.ChatInput;
import com.capsule.dialogue.dto.ChatLine;
import com.capsule.dialogue.dto.ChatMode;
import com.capsule.dialogue.dto.ChatOutcome;
import com.capsule.dialogue.dto.LlmContext;
import com.capsule.dialogue.dto.MessageRequest;
import com.capsule.dialogue.dto.MessageResponse;
import com.capsule.dialogue.dto.ReactionCharacter;
import com.capsule.dialogue.dto.ReactionLine;
import com.capsule.dialogue.dto.ReactionResponse;
import com.capsule.dialogue.repository.ChatMessageRepository;
import com.capsule.dialogue.repository.ConversationRecapRepository;
import com.capsule.dialogue.repository.ConversationRepository;
import com.capsule.relation.api.RelationCommands;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.api.RelationTypes.AffinityChange;
import com.capsule.relation.api.RelationTypes.ChatScore;
import com.capsule.relation.api.RelationTypes.RelationView;
import com.capsule.relation.api.RelationTypes.SignalType;
import com.capsule.session.api.ConnectionMessageCounter;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상태 반응(api.md 19), 메시지(20), 공부 종료 때 대화 마무리(22, session이 부름).
 * 여기서는 API 규칙(검증·재전송·저장·마감 대체 대사)만 지키고, LLM이 쓰는 글은 ReactionWriter · ChatPipeline · ClosingWriter가 맡는다.
 * 메시지의 LLM 호출은 DB 트랜잭션 밖에서 한다: 읽기 → LLM → 저장(한 트랜잭션).
 */
@Service
public class DialogueService {

    private static final Logger log = LoggerFactory.getLogger(DialogueService.class);

    private static final ExecutorService LLM_THREADS = Executors.newVirtualThreadPerTaskExecutor();
    /** 세션 카드 머리말 날짜 (예: "11/03 52분") */
    private static final DateTimeFormatter CARD_DATE = DateTimeFormatter.ofPattern("MM/dd");
    /** 고급 대화 1턴 크레딧 (api.md 33 '크레딧 규칙') */
    public static final int ADVANCED_COST = 30;
    /** 보이스 대화 1턴 크레딧 (api.md 33 '크레딧 규칙', 28 '음성') */
    public static final int VOICE_COST = 20;

    private final SessionQuery sessions;
    private final ConnectionMessageCounter messageCounter;
    private final CharacterQuery characters;
    private final ReactionQuery reactions;
    private final RelationQuery relationQuery;
    private final RelationCommands relationCommands;
    private final MemoryQuery memories;
    private final UserQuery users;
    private final RateLimiter rateLimiter;
    private final ReactionWriter reactionWriter;
    private final FixedLines fixedLines;
    private final List<ChatPipeline> pipelines;
    private final ChatReplier replier;
    private final ChatScorer scorer;
    private final CharacterContents characterContents;
    private final MemoryKeeper memoryKeeper;
    private final ClosingWriter closingWriter;
    private final ConversationRepository conversations;
    private final ConversationRecapRepository recapRepository;
    private final ChatMessageRepository messages;
    private final TransactionTemplate tx;
    private final RuntimeSettings<DialogueProperties> props;
    private final Clock clock;
    private final CreditQuery creditQuery;
    private final CreditCommands creditCommands;
    private final SafetyGuard safety;
    private final BackgroundPicker backgroundPicker;
    private final VoiceStreams voices;
    /** 고급 모드 답장 모델. 비어 있으면 기본 모드와 같은 모델 (api.md 20, 2026-11-23) */
    private final String advancedModel;

    public DialogueService(
            SessionQuery sessions,
            ConnectionMessageCounter messageCounter,
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
            PlatformTransactionManager transactionManager,
            RuntimeSettings<DialogueProperties> props,
            Clock clock,
            CreditQuery creditQuery,
            CreditCommands creditCommands,
            SafetyGuard safety,
            BackgroundPicker backgroundPicker,
            VoiceStreams voices,
            // TODO(judgment): J-16 고급 모드에 쓸 실제 모델 (운영 설정 capsule.llm.advanced-model). 정해지기 전까지 비워 두면 기본 모델
            @Value("${capsule.llm.advanced-model:}") String advancedModel) {
        this.sessions = sessions;
        this.messageCounter = messageCounter;
        this.characters = characters;
        this.reactions = reactions;
        this.relationQuery = relationQuery;
        this.relationCommands = relationCommands;
        this.memories = memories;
        this.users = users;
        this.rateLimiter = rateLimiter;
        this.reactionWriter = reactionWriter;
        this.fixedLines = fixedLines;
        this.pipelines = pipelines;
        this.replier = replier;
        this.scorer = scorer;
        this.characterContents = characterContents;
        this.memoryKeeper = memoryKeeper;
        this.closingWriter = closingWriter;
        this.conversations = conversations;
        this.recapRepository = recapRepository;
        this.messages = messages;
        this.tx = new TransactionTemplate(transactionManager);
        this.props = props;
        this.clock = clock;
        this.creditQuery = creditQuery;
        this.creditCommands = creditCommands;
        this.safety = safety;
        this.backgroundPicker = backgroundPicker;
        this.voices = voices;
        this.advancedModel = advancedModel;
    }

    /** 지금 설정의 답장 파이프라인. 운영 설정에서 바꾸면 다음 메시지부터 (이름은 저장할 때 검사함) */
    private ChatPipeline chat() {
        String name = props.current().chatPipeline();
        return pipelines.stream()
                .filter(p -> p.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("capsule.dialogue.chat-pipeline을 모름: " + name));
    }

    // --- 19 상태 반응 ---

    public ReactionResponse react(
            long userId, long sessionId, long eventId, UUID requestId, Integer requestedIntensity, Boolean tts) {
        SessionInfo session = openSession(userId, sessionId);
        SessionEventInfo event = sessions.findEvent(sessionId, eventId)
                .orElseThrow(() -> new ApiException(DialogueErrorCode.EVENT_NOT_FOUND));
        long characterId = session.characterId();
        String trigger = triggerOf(event);
        // 강도는 AWAY에만 (없으면 1). 다른 trigger는 보내도 무시
        Integer intensity = trigger.equals("AWAY") ? (requestedIntensity == null ? 1 : requestedIntensity) : null;
        if (intensity != null && (intensity < 1 || intensity > props.current().awayIntensityMax())) {
            throw new ApiException(CommonErrorCode.INVALID_REQUEST);
        }

        // 호감도는 상태 변화 전송(18)이 반영하고 그 응답으로 돌려준다. 여기서는 관계 단계(호감도 구간)만 읽어 말투를 고른다
        int stage = relationQuery.view(userId, characterId).stage();
        // 상태 변화는 LLM이 대사 + 표정 (ADR 0022 결정 18), 동작 이벤트는 미리 쓴 대사(캐릭터 설정, 순서대로 → 없으면 공통 고정 대사).
        // LLM이 마감(llm-budget)을 넘기거나 실패해도 미리 쓴 대사로
        ReactionLine line = null;
        boolean fallback = false;
        if (reactionWriter.usesLlm(trigger)) {
            // 상태 변화는 LLM: 관계 단계 + 지난 세션 흐름 + 이번 세션 최근 대화를 같이 넣는다
            CharacterInfo character = character(characterId);
            String nickname = nickname(userId);
            Optional<Conversation> conv = conversations.findByUserIdAndCharacterId(userId, characterId);
            String summary = conv.map(c -> recaps(c.getId())).orElse(null);
            List<ChatLine> recent = conv.map(c -> PromptVars.tail(
                            promptLines(
                                    userId, messages.findByConversationIdAndSessionIdOrderById(c.getId(), sessionId)),
                            props.current().reactionContextLines()))
                    .orElse(List.of());
            LlmContext ctx = LlmContext.app(userId, sessionId, deadline());
            Optional<ReactionLine> generated = withinBudget(() -> reactionWriter.generate(
                    character, trigger, intensity, stage, nickname, summary, recent, Map.of(), ctx));
            fallback = generated.isEmpty();
            line = generated.orElse(null);
        }
        if (line == null) {
            line = characterContents
                    .next(
                            characterId,
                            FixedLines.key(trigger, intensity),
                            trigger,
                            stage,
                            String.valueOf(sessionId),
                            nickname(userId))
                    .orElseGet(() -> fixedLines.reaction(characterId, trigger, intensity, stage, Map.of()));
            // TODO(judgment): J-12 ② LLM이 실패한 대체 대사의 감정. 지금은 없음(동작 없음), 추천은 그 대사 칸의 표정
            if (fallback) line = new ReactionLine(line.content(), null);
        }
        // 명세 19: 모션·표정은 대사의 감정으로 고른다 (trigger 기준 '알아채는' 동작은 18이 줌)
        // TODO(judgment): J-04 ④ 강도를 동작에도 쓸지. 지금은 대사(고정 대사 칸 · LLM 입력)에만
        // 감정 후보는 캐릭터 표정 목록(character_profiles.expressions, 결정 모델이 고름, ADR 0028).
        // 설정이 없는 캐릭터만 공통 후보(capsule.dialogue.emotions)
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
        return reply(accept(userId, sessionId, request), null);
    }

    /**
     * 받아 둔 메시지 (명세 20 · 28): 검증 · 재전송 확인 · 사용자 말 저장 · 속도 제한까지 끝난 상태.
     * answered가 있으면 이미 답한 재전송, inFlightSince가 있으면 같은 requestId가 처리 중.
     * voice는 이번 턴에 음성을 보낼지 (28만, {@link #withVoice})
     */
    public record Accepted(
            SessionInfo session,
            Conversation conversation,
            MessageRequest request,
            MessageResponse answered,
            Instant inFlightSince,
            boolean voice) {

        public Accepted(
                SessionInfo session,
                Conversation conversation,
                MessageRequest request,
                MessageResponse answered,
                Instant inFlightSince) {
            this(session, conversation, request, answered, inFlightSince, false);
        }

        /** 새로 답할 요청인지 (재전송 · 처리 중 대기가 아님) */
        public boolean fresh() {
            return answered == null && inFlightSince == null;
        }
    }

    /**
     * 28 음성 (명세 28 '음성', ADR 0034): tts 요청 + 설정 voiceEnabled + 크레딧(보이스 20, 고급이면 50)이면 음성을 켠다.
     * 아니면 그대로(음성 없이 글만, 에러 아님). 재전송 · 처리 중 대기는 음성을 보내지 않는다
     */
    public Accepted withVoice(Accepted a) {
        if (!a.fresh() || !Boolean.TRUE.equals(a.request().tts())) return a;
        long userId = a.session().userId();
        if (!voices.enabled(userId)) return a;
        int cost = VOICE_COST + (a.request().modeOrBasic() == ChatMode.ADVANCED ? ADVANCED_COST : 0);
        Credits credits = tx.execute(s -> creditQuery.current(userId));
        if (credits.todayRemaining() < cost) return a;
        return new Accepted(a.session(), a.conversation(), a.request(), null, null, true);
    }

    /** 이 턴의 음성 스트림 (withVoice가 켠 요청만). 받은 음성 조각을 sink로 (false를 돌려주면 음성을 멈춤) */
    public VoiceStream voiceStream(Accepted a, java.util.function.Predicate<VoiceStream.AudioChunk> sink) {
        return voices.open(a.session().userId(), a.session().characterId(), sink);
    }

    public Duration voiceWait() {
        return voices.waitAfterDone();
    }

    /** 20 · 28 앞부분. 여기서 나는 오류는 보통 HTTP 오류다 (28은 스트림을 열기 전) */
    public Accepted accept(long userId, long sessionId, MessageRequest request) {
        SessionInfo session = openSession(userId, sessionId);
        if (request.characterId() != session.characterId()) {
            throw new ApiException(DialogueErrorCode.CHARACTER_NOT_IN_SESSION);
        }
        Conversation conversation = conversation(userId, session.characterId());

        // 재전송은 한도 검사 전에 돌려준다: 응답을 못 받아 다시 보낸 요청이 429가 되면 안 됨
        Optional<MessageResponse> answered = answered(conversation, session, request);
        if (answered.isPresent()) {
            return new Accepted(session, conversation, request, answered.get(), null);
        }
        Optional<ChatMessage> asked = question(conversation, request);
        if (asked.isPresent()) {
            return new Accepted(
                    session, conversation, request, null, asked.get().getCreatedAt());
        }
        // 안전 위반 반복이면 정지 중 (명세 20 '안전'). 재전송(이미 답함 · 처리 중)은 위에서 그대로 돌려준다
        Optional<Instant> suspended = safety.suspendedUntil(userId);
        if (suspended.isPresent()) {
            throw new ApiException(DialogueErrorCode.CHAT_SUSPENDED, Map.of("until", suspended.get()));
        }
        // 고급 모드는 LLM 전에 잔액을 본다 (명세 20 '대화 모드 · 크레딧'). 모자라면 아무것도 저장하지 않고 409.
        // 크레딧 조회는 그날 출석분 지급 · 소멸을 쓰므로 쓰기 트랜잭션 안에서 (CreditQuery.current)
        if (request.modeOrBasic() == ChatMode.ADVANCED) {
            Credits credits = tx.execute(s -> creditQuery.current(userId));
            if (credits.todayRemaining() < ADVANCED_COST) {
                throw new ApiException(DialogueErrorCode.CREDIT_INSUFFICIENT, Map.of("credits", credits));
            }
        }
        // 한도 검사보다 먼저 저장한다: 같은 requestId가 동시에 오면 늦은 쪽은 UNIQUE에 막혀 먼저 온 쪽의 답장을 기다린다.
        // 그래서 처리 중 재전송은 횟수에 세지 않는다(명세 20). 저장은 바로 커밋되므로 429면 이 사용자 말을 지운다
        try {
            messages.saveAndFlush(ChatMessage.question(
                    conversation.getId(),
                    sessionId,
                    request.requestId(),
                    request.content(),
                    request.inputType().name(),
                    Instant.now(clock)));
        } catch (DataIntegrityViolationException e) {
            // 다른 쪽이 먼저 저장했으면 그 답을 기다린다. 다른 이유의 무결성 오류면 그대로 던진다
            ChatMessage other = question(conversation, request).orElseThrow(() -> e);
            return new Accepted(session, conversation, request, null, other.getCreatedAt());
        }
        if (!rateLimiter.tryAcquire(
                "rate:chat:" + userId,
                props.current().messageLimit(),
                props.current().messageLimitWindow())) {
            messages.deleteQuestion(conversation.getId(), request.requestId());
            throw new ApiException(CommonErrorCode.TOO_MANY_REQUESTS);
        }
        return new Accepted(session, conversation, request, null, null);
    }

    /**
     * 20 · 28 뒷부분: 답장을 만들어 저장한다.
     *
     * @param onReply null이 아니면 스트리밍(명세 28): 지금까지의 대사를 넘긴다. 재전송 · 처리 중 대기는 부르지 않는다
     */
    public MessageResponse reply(Accepted a, Consumer<String> onReply) {
        if (a.answered() != null) return a.answered();
        if (a.inFlightSince() != null)
            return awaitAnswer(a.session(), a.conversation(), a.request(), a.inFlightSince());
        return answer(a.session(), a.conversation(), a.request(), onReply, a.voice());
    }

    /**
     * 읽기 → LLM(트랜잭션 밖, llm-budget) → 저장(한 트랜잭션: 호감도 · 답장).
     * 저장 트랜잭션은 연결의 대화 수(countReply)부터 올린다 (2026-11-23): 연결 행을 잠그고, 공부 종료(22)도 연결 행을 먼저 잠근 채
     * 커밋까지 간다. 그래서 종료와 겹치면 여기서 종료가 끝나길 기다린 뒤 끝난 연결이라 0행 → 아무것도 남기지 않는다.
     * 종료보다 먼저 잡으면 이 답장까지 커밋된 뒤에 종료가 정산·요약한다. 잠금 순서가 종료(연결 → relation → 대화)와 같아 교착이 없다
     * (호감도 applyChat이 relation 행을 잠그므로 반드시 그 앞에서). 크레딧(사용자 잠금)은 트랜잭션 맨 끝에 잡는다 (2026-11-23).
     * 안전 위반이면 LLM을 부르지 않고 거절 대사 · 0점 · 위반 기록 (명세 20 '안전')
     */
    private MessageResponse answer(
            SessionInfo session,
            Conversation conversation,
            MessageRequest request,
            Consumer<String> onReply,
            boolean voice) {
        long userId = session.userId();
        long sessionId = session.id();
        long characterId = session.characterId();
        UUID requestId = request.requestId();

        List<ChatMessage> earlier =
                messages.findByConversationIdAndSessionIdOrderById(conversation.getId(), sessionId).stream()
                        .filter(m -> !m.getRequestId().equals(requestId))
                        .toList();
        // 신고로 가린 답장(41) · 안전 위반 턴은 이전 대화에서도 뺀다
        List<ChatLine> history = promptLines(userId, earlier);
        CharacterInfo character = character(characterId);
        ChatMode mode = request.modeOrBasic();
        ChatInput input = new ChatInput(
                character,
                nickname(userId),
                relationQuery.view(userId, characterId).stage(),
                recaps(conversation.getId()),
                memoryKeeper.facts(userId, characterId),
                memoryLines(userId),
                history,
                request.content(),
                Map.of(),
                sessions.onBreak(sessionId),
                chatty(earlier),
                (int) Duration.between(session.startedAt(), Instant.now(clock)).toMinutes());
        // 안전 (명세 20 '안전'): LLM에 넘기기 전에 판정. 위반이면 LLM을 부르지 않고 정해진 거절 대사
        Optional<String> violation = safety.violation(request.content());
        LlmContext ctx = LlmContext.app(userId, sessionId, deadline())
                .withChatModel(mode == ChatMode.ADVANCED ? advancedModel : null);
        Optional<ChatOutcome> outcome = violation.isPresent()
                ? Optional.empty()
                : onReply == null
                        ? withinBudget(() -> replier.reply(chat(), input, ctx))
                        : streamed(input, ctx, onReply);
        String reply = violation.isPresent()
                ? safety.refusalLine()
                : outcome.map(ChatOutcome::reply)
                        .orElseGet(() -> characterContents
                                .next(
                                        characterId,
                                        "REPLY_FALLBACK",
                                        null,
                                        input.stage(),
                                        String.valueOf(sessionId),
                                        input.nickname())
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
                                .filter(m -> USER.equals(m.getSender()) && m.getSignal() != null)
                                .map(m -> SignalType.valueOf(m.getSignal()))
                                .toList());

        // 배경 (명세 20 '배경', ADR 0032): LLM 답장일 때만 결정 모델이 고른다 (fallback · 안전 경고 턴은 그대로). 트랜잭션 밖에서
        Optional<BackgroundInfo> background = outcome.isPresent()
                ? backgroundPicker.pick(sessionId, characterId, character.name(), request.content(), reply, ctx)
                : Optional.empty();

        MessageResponse response;
        Credits[] shortOf = {null}; // 답하는 사이 크레딧이 모자라짐 → 409 (아래)
        try {
            response = tx.execute(status -> {
                // 진행 중이면 연결의 대화 수 +1 (명세 22 messageCount, 2026-11-23). 끝났으면 0행.
                // 연결 행 잠금이 relation 잠금(applyChat)보다 먼저여야 종료와 잠금 순서가 같다 (위 Javadoc)
                if (!messageCounter.countReply(sessionId)) {
                    status.setRollbackOnly(); // 답하는 사이 공부가 끝났다: 정산·요약이 끝난 세션에 남기지 않는다
                    return null;
                }
                AffinityChange affinity =
                        relationCommands.applyChatScore(userId, characterId, sessionId, requestId, score);
                if (signal != null) messages.markSignal(conversation.getId(), requestId, signal.name());
                ChatMessage saved = messages.save(ChatMessage.reply(
                        conversation.getId(), sessionId, requestId, reply, mode.name(), Instant.now(clock)));
                violation.ifPresent(category -> safety.record(userId, sessionId, requestId, category));
                background.ifPresent(b -> backgroundPicker.save(sessionId, b)); // 연결 행은 countReply가 이미 잠금
                // 크레딧은 맨 끝에 (사용자 잠금, 연결 → relation → 대화 → credit 순서라 종료와 교착이 없다).
                // 고급 모드 · 보이스는 LLM 답장이 성공했을 때만 깎는다 (fallback · 안전 경고 턴은 안 깎음, 같은 requestId는 한 번만).
                // 둘 다면 한 줄로 50 (명세 33 '크레딧 규칙', 28 '음성')
                boolean llmReply = outcome.isPresent();
                boolean advanced = mode == ChatMode.ADVANCED;
                int charged = 0;
                Credits credits = null;
                if (llmReply && (advanced || voice)) {
                    boolean voicePart = voice; // 이번에 보이스 몫까지 깎는지
                    int cost = (advanced ? ADVANCED_COST : 0) + (voicePart ? VOICE_COST : 0);
                    SpendResult spent = creditCommands.spend(
                            userId, cost, spendTitle(advanced, voicePart, character.name()), requestId);
                    if (!spent.spent() && advanced && voicePart) {
                        // 음성을 켠 뒤 잔액이 모자라짐: 고급 모드 몫만이라도 (음성은 이미 나가는 중이라 그대로 둠)
                        voicePart = false;
                        cost = ADVANCED_COST;
                        spent = creditCommands.spend(
                                userId, cost, spendTitle(true, false, character.name()), requestId);
                    }
                    if (spent.spent()) {
                        charged = cost;
                        // 재전송 charged를 처음과 같게: 보이스 몫까지 깎였는지 남긴다
                        if (voicePart) saved.markVoiceCharged();
                    } else if (advanced) {
                        // 잔액을 확인한 뒤 다른 요청이 먼저 써서 모자라졌다: 409로 되돌린다 (아무것도 남기지 않음, 명세 20).
                        // 그래서 저장된 고급 모드 LLM 답장은 늘 깎인 것이다 (재전송 charged의 근거)
                        shortOf[0] = spent.credits();
                        status.setRollbackOnly();
                        return null;
                    }
                    // 보이스만 모자라짐: 깎지 않는다 (그 기능만 멈춤, 음성은 이미 나가는 중)
                    credits = spent.credits();
                } else if (advanced || voice) {
                    credits = creditQuery.current(userId);
                }
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
                        violation.isEmpty() && outcome.isEmpty(),
                        audio(request.tts()),
                        mode,
                        charged,
                        credits,
                        violation.isPresent() ? MessageResponse.SAFETY_WARNING : null,
                        background
                                .map(b -> new MessageResponse.Background(b.code(), b.name(), b.imageUrl()))
                                .orElse(null),
                        voice);
            });
        } catch (DataIntegrityViolationException e) {
            // 기다리다 이어받은 요청과 먼저 온 요청이 둘 다 답했다: 먼저 저장된 답장으로
            return answered(conversation, session, request).orElseThrow(() -> e);
        }
        if (response == null) {
            // 답을 못 받은 사용자 말은 지운다 (대화방 기록에 남지 않게). 종료 카드에 이미 들어갔으면 카드에만 남는다
            messages.deleteQuestion(conversation.getId(), requestId);
            if (shortOf[0] != null) {
                throw new ApiException(DialogueErrorCode.CREDIT_INSUFFICIENT, Map.of("credits", shortOf[0]));
            }
            throw new ApiException(DialogueErrorCode.CONNECTION_ENDED);
        }
        // 기억 ①: 답장을 저장한 뒤 이번 사용자 말에서 기억할 것을 정리한다 (답장을 늦추지 않게 따로, 마감 없음)
        Runnable remember = () -> memoryKeeper.afterTurn(
                character,
                userId,
                requestId,
                request.content(),
                PromptVars.tail(history, props.current().signalContextLines()),
                LlmContext.app(userId, sessionId, null));
        if (!props.current().memoryEnabled() || violation.isPresent()) return response; // 위반한 말은 기억하지 않는다
        if (props.current().memoryAsync()) LLM_THREADS.execute(remember);
        else remember.run();
        return response;
    }

    /**
     * 같은 requestId가 아직 처리 중: 끝날 때까지 기다렸다가 같은 답장 (명세 20).
     * 먼저 온 요청이 사용자 말을 저장한 지 in-flight-wait가 지나도 답이 없으면(중간에 죽음) 이 요청이 이어서 답한다.
     */
    private MessageResponse awaitAnswer(
            SessionInfo session, Conversation conversation, MessageRequest request, Instant askedAt) {
        Instant deadline = askedAt.plus(props.current().inFlightWait());
        while (Instant.now(clock).isBefore(deadline)) {
            Optional<MessageResponse> answered = answered(conversation, session, request);
            if (answered.isPresent()) {
                return answered.get();
            }
            try {
                Thread.sleep(props.current().inFlightPoll());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        return answer(session, conversation, request, null, false); // 이어받은 요청은 음성 없이 (처리 중 대기와 같음)
    }

    /**
     * 명세 28: 답장을 스트리밍으로 만들며 대사를 흘려 보낸다.
     * 첫 대사를 llm-budget 안에 못 보내면 대체 대사(20과 같은 시간). 첫 대사를 보낸 뒤에는 끝까지 기다린다:
     * 이미 보인 글을 대체 대사로 바꾸지 않으려고. 끝까지의 상한은 LLM http-timeout이다
     */
    private Optional<ChatOutcome> streamed(ChatInput input, LlmContext ctx, Consumer<String> onReply) {
        // 0 = 첫 대사 전, 1 = 흘려 보내는 중, 2 = 대체 대사로 넘어감 (넘어간 뒤 온 글은 보내지 않는다)
        AtomicInteger state = new AtomicInteger();
        String[] last = {null};
        Consumer<String> onText = raw -> {
            String part = ReplyStream.partial(raw);
            if (part == null || part.isBlank() || part.equals(last[0])) return;
            if (state.compareAndSet(0, 1) || state.get() == 1) {
                last[0] = part;
                try {
                    onReply.accept(part);
                } catch (RuntimeException e) {
                    // 화면 쓰기 실패를 LLM 실패로 바꾸지 않는다 (게이트웨이가 다른 공급자로 다시 부르면 비용 두 번)
                    log.debug("스트림 대사 보내기 실패: {}", e.toString());
                }
            }
        };
        CompletableFuture<ChatOutcome> call =
                CompletableFuture.supplyAsync(() -> replier.reply(chat(), input, ctx, onText), LLM_THREADS);
        try {
            return Optional.ofNullable(call.get(props.current().llmBudget().toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException e) {
            if (state.compareAndSet(0, 2)) {
                // 기다리기만 멈춘다: 진행 중인 LLM 호출은 끝까지 돌고 기록된다(20과 같음). 그 뒤 온 글은 state 2라 안 보낸다
                log.warn("스트리밍 답장의 첫 대사가 {} 안에 안 옴, 고정 대사로 대체", props.current().llmBudget());
                return Optional.empty();
            }
            return await(call);
        } catch (ExecutionException e) {
            log.warn("LLM 호출 실패, 고정 대사로 대체", e.getCause());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /** 명세 19·20: 감정 이름으로 반응 표에서 모션·표정을 고른다. 감정이 없거나 그 감정 행이 없으면 동작 없음 */
    // TODO(judgment): J-07 ②·J-12 ② 감정이 없을 때(LLM 마감 초과 등) 기본 감정을 쓸지. 지금은 동작 없음
    private Reaction emotionReaction(long characterId, String emotion) {
        if (emotion == null) return Reaction.NONE;
        return reactions.pick(characterId, emotion, null);
    }

    /**
     * 이미 답한 requestId면 저장된 답장. mode는 저장된 답장의 모드(요청 값이 아니라), safety는 위반 기록으로,
     * charged는 처음 답할 때와 같게: 고급 모드이고 위반 턴이 아니고 LLM 답장이었으면(사용자 말에 종류가 남음 = fallback 아님) 30.
     * credits는 고급 모드면 지금 크레딧 (재전송은 다시 깎지 않는다)
     */
    private Optional<MessageResponse> answered(Conversation conversation, SessionInfo session, MessageRequest request) {
        // ponytail: 재전송 때 원래 delta(와 감정·모션·표정·fallback)는 다시 보여주지 않음 (명세 20). 필요하면 chat_messages에 같이 저장
        return messages.findByConversationIdAndRequestIdAndSender(conversation.getId(), request.requestId(), CHARACTER)
                .map(m -> {
                    long userId = session.userId();
                    ChatMode mode = m.getMode() == null ? ChatMode.BASIC : ChatMode.valueOf(m.getMode());
                    boolean warned = safety.struck(userId, request.requestId());
                    int charged = 0;
                    Credits credits = null;
                    boolean advanced = mode == ChatMode.ADVANCED;
                    if (advanced || m.isVoiceCharged()) {
                        boolean llmReply = !warned
                                && question(conversation, request)
                                        .map(q -> q.getSignal() != null)
                                        .orElse(false);
                        if (llmReply) charged = (advanced ? ADVANCED_COST : 0) + (m.isVoiceCharged() ? VOICE_COST : 0);
                        credits = tx.execute(s -> creditQuery.current(userId));
                    }
                    return new MessageResponse(
                            request.requestId(),
                            session.characterId(),
                            m.getContent(),
                            null,
                            null,
                            null,
                            unchanged(userId, session.characterId()),
                            false,
                            audio(request.tts()),
                            mode,
                            charged,
                            credits,
                            warned ? MessageResponse.SAFETY_WARNING : null,
                            null, // 배경은 처음 응답에서만 (재전송은 감정 · 표정처럼 null, 명세 20 '배경')
                            false); // 재전송은 음성 없이 (명세 28 '음성')
                });
    }

    // --- 22 공부 종료 ---

    /** 공부 종료 초안. pendingIds = 카드에 넣은 메시지 (저장할 때 recapped_at 표시) */
    public record ClosingDraft(List<Long> pendingIds, Optional<String> card, String line)
            implements SessionClosingHandler.Draft {}

    /**
     * 공부 종료 1단계: 트랜잭션 밖에서 이번 세션 대화를 흐름 카드 한 장으로 쓰고 완료 대사를 만든다 (기억 ②). DB에 쓰지 않는다:
     * LLM을 기다리는 동안 DB 연결을 잡지 않게 (개발 서버 풀 2).
     * 카드에 아직 안 넣은 메시지를 전부 쓴다: 이번 연결 것 + 앞선 종료에서 카드 쓰기가 실패한 것 (2026-11-23부터 지우지 않고 표시).
     * LLM이 실패하거나 마감을 넘겨도 공부 종료는 성공해야 하므로 고정 문구로 대신한다. 카드와 완료 대사는 같이 보낸다(둘 다 llm-budget 안).
     */
    public ClosingDraft draft(SessionClosing closing) {
        List<ChatMessage> pending = conversations
                .findByUserIdAndCharacterId(closing.userId(), closing.characterId())
                .map(c -> messages.findByConversationIdAndRecappedAtIsNullOrderById(c.getId()))
                .orElse(List.of());
        CharacterInfo character = character(closing.characterId());
        String nickname = nickname(closing.userId());
        LlmContext ctx = LlmContext.app(closing.userId(), closing.sessionId(), deadline());
        String header = LocalDate.now(clock.withZone(ClockConfig.KST)).format(CARD_DATE) + " "
                + closing.studySeconds() / 60 + "분";

        // 가린 답장 · 안전 위반 턴은 카드에도 넣지 않는다 (2026-11-23). 카드에 넣었다는 표시(pendingIds)는 같이 한다
        List<ChatLine> cardLines = promptLines(closing.userId(), pending);
        CompletableFuture<String> recap = cardLines.isEmpty()
                ? null
                : start(() -> closingWriter.recap(
                        character,
                        nickname,
                        header,
                        memoryKeeper.facts(closing.userId(), closing.characterId()),
                        cardLines,
                        Map.of(),
                        ctx));
        // 캐릭터 설정에 완료 대사가 있으면 그것 (PM: 말포이는 고정 문장, 관계 단계별), 없으면 LLM.
        // 목표 시간의 절반도 못 채우고 직접 끝냈으면 공부 포기 대사(GIVE_UP, 없으면 완료 대사)
        int stage = relationQuery.view(closing.userId(), closing.characterId()).stage();
        // 세션마다 한 번 나오는 대사라 순번을 세션이 아니라 사용자 단위로 잇는다: 세션 단위면 늘 그 단계의 첫 줄만 나온다
        // ponytail: 순번은 메모리라 서버를 다시 띄우면 첫 줄부터. 연달아 같은 문장만 피하면 돼서 저장하지 않음
        Optional<String> scriptedClosing = characterContents
                .next(
                        closing.characterId(),
                        gaveUp(closing, props.current().giveUpRatio()) ? "GIVE_UP" : "CLOSING",
                        "CLOSING",
                        stage,
                        "user-" + closing.userId(),
                        nickname)
                .map(ReactionLine::content);
        CompletableFuture<String> line = scriptedClosing.isPresent()
                ? CompletableFuture.completedFuture(scriptedClosing.get())
                : start(() -> closingWriter.closingLine(
                        character, nickname, closing.studySeconds(), closing.endType(), Map.of(), ctx));

        Optional<String> card = recap == null ? Optional.empty() : await(recap);
        String text = await(line).orElseGet(() -> fixedLines.closingFallback(closing.characterId()));
        return new ClosingDraft(pending.stream().map(ChatMessage::getId).toList(), card, text);
    }

    /** 공부 종료 2단계: 공부 종료 트랜잭션 안에서 초안을 저장하고 완료 대사를 돌려준다. draft가 null이면 여기서 만든다 (자동 마감 배치) */
    @Transactional
    public String close(SessionClosing closing, ClosingDraft draft) {
        ClosingDraft d = draft != null ? draft : draft(closing);
        if (d.card().isPresent()) {
            Conversation conversation = conversation(closing.userId(), closing.characterId());
            recapRepository.save(new ConversationRecap(
                    conversation.getId(), closing.sessionId(), d.card().get(), Instant.now(clock)));
            // 최근 recap-max장만 남긴다. 오래된 흐름은 지워져도 된다 (오래 남길 사실은 기억 ①에 있음)
            List<ConversationRecap> all = recapRepository.findByConversationIdOrderByIdDesc(conversation.getId());
            int keep = props.current().recapMax(); // 한 번만 읽기: 사이에 커지면 subList가 범위를 넘는다
            if (all.size() > keep) recapRepository.deleteAll(all.subList(keep, all.size()));
            // 지우지 않는다: 32 대화방 기록으로 남김 (2026-11-23). 다음 카드에 또 들어가지 않게 표시만
            if (!d.pendingIds().isEmpty()) messages.markRecapped(d.pendingIds(), Instant.now(clock));
        }
        // 카드 쓰기에 실패하면 메시지를 남긴다: 다음 종료 때 그 세션 카드에 같이 들어간다
        return d.line();
    }

    // --- 공통 ---

    /**
     * 베타 이벤트(2026-11-23): IDLE_TALK(말 걸기 타이머)는 IDLE, 나머지(PRESENT · AWAY · RETURN)는 그대로.
     * 옛 FOCUS 기록은 세션 첫 FOCUS면 GREETING, 아니면 BREAK_END. IDLE 대사 콘텐츠는 아직 없음 (확정 필요)
     */
    private static String triggerOf(SessionEventInfo event) {
        return switch (event.type()) {
            case "IDLE_TALK" -> "IDLE";
            case "FOCUS" -> event.firstFocusOfSession() ? "GREETING" : "BREAK_END";
            default -> event.type();
        };
    }

    private SessionInfo openSession(long userId, long sessionId) {
        SessionInfo session = sessions.find(sessionId)
                .filter(s -> s.userId() == userId)
                .orElseThrow(() -> new ApiException(DialogueErrorCode.CONNECTION_NOT_FOUND));
        if (!session.inProgress()) {
            throw new ApiException(DialogueErrorCode.CONNECTION_ENDED);
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
                .limit(props.current().recapMax())
                .map(ConversationRecap::getContent)
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

    /** 직접 끝냈는데 목표 시간의 ratio(give-up-ratio)도 못 채웠으면 공부 포기 (자동 종료는 포기로 보지 않는다) */
    // TODO(judgment): J-15 '공부 포기'의 기준 (승희 2026-11-05 임시: 직접 끝냈는데 목표 시간의 절반 미만)
    public static boolean gaveUp(SessionClosing closing, double ratio) {
        Integer target = closing.targetMinutes();
        return "NORMAL".equals(closing.endType())
                && target != null
                && target > 0
                && closing.studySeconds() < target * 60 * ratio;
    }

    /**
     * LLM 입력(답장 이전 대화 · 상태 반응 최근 대화 · 흐름 카드)에 넣을 줄. 신고로 가린 답장(41)과
     * 안전 위반 턴(사용자 말 · 거절 대사, 20 '안전')은 뺀다 (2026-11-23)
     */
    private List<ChatLine> promptLines(long userId, List<ChatMessage> chat) {
        java.util.Set<UUID> struck = safety.struckAmong(
                userId, chat.stream().map(ChatMessage::getRequestId).distinct().toList());
        return lines(chat.stream()
                .filter(m -> !m.hidden() && !struck.contains(m.getRequestId()))
                .toList());
    }

    private static List<ChatLine> lines(List<ChatMessage> chat) {
        return chat.stream()
                .map(m -> new ChatLine(m.getSender(), m.getContent()))
                .toList();
    }

    /**
     * 최근 chatty-window(10분)에 사용자 말이 이번 말까지 chatty-messages(8번) 이상이면 수다가 길어진 것. 공부 중일 때만 쓰인다(파이프라인이 거름).
     * ponytail: 쉬는 시간에 한 말도 센다. 쉬는 시간 직후 첫 말에 한 번 더 돌려보낼 수 있다. 문제가 되면 마지막 상태 이벤트 뒤의 말만 센다
     */
    // TODO(judgment): J-14 '수다가 길어졌다'의 기준 (승희 2026-11-05 임시: 최근 10분에 사용자 말 8번, 이번 말 포함)
    private boolean chatty(List<ChatMessage> earlier) {
        Instant since = Instant.now(clock).minus(props.current().chattyWindow());
        long recent = earlier.stream()
                .filter(m -> USER.equals(m.getSender()) && m.getCreatedAt().isAfter(since))
                .count();
        return recent + 1 >= props.current().chattyMessages();
    }

    private Optional<ChatMessage> question(Conversation conversation, MessageRequest request) {
        return messages.findByConversationIdAndRequestIdAndSender(conversation.getId(), request.requestId(), USER);
    }

    /** 서비스 LLM 호출의 마감: 이 시각이 지나면 게이트웨이가 새 호출을 시작하지 않는다 (결과가 버려지므로) */
    private Instant deadline() {
        return Instant.now(clock).plus(props.current().llmBudget());
    }

    private <T> CompletableFuture<T> start(Supplier<T> call) {
        return CompletableFuture.supplyAsync(call, LLM_THREADS)
                .orTimeout(props.current().llmBudget().toMillis(), TimeUnit.MILLISECONDS);
    }

    /** llm-budget 안에 끝나지 않거나 실패하면 빈 값 → 호출한 쪽이 고정 대사를 쓴다 */
    private static <T> Optional<T> await(CompletableFuture<T> call) {
        try {
            return Optional.ofNullable(call.join());
        } catch (CompletionException e) {
            log.warn("LLM 호출 실패, 고정 대사로 대체", e.getCause());
            return Optional.empty();
        }
    }

    private <T> Optional<T> withinBudget(Supplier<T> call) {
        return await(start(call));
    }

    /**
     * 19 · 20 응답의 audioBase64는 늘 null: 답장 음성은 28 스트리밍의 audio 이벤트로만 (명세 28 '음성', ADR 0034).
     * 상태 반응(19) 음성은 아직 없음
     */
    private static String audio(Boolean tts) {
        return null;
    }

    /** 크레딧 내역 문구 (명세 34): "고급 대화 · 채린" · "보이스 대화 · 채린" · "고급 · 보이스 대화 · 채린" */
    static String spendTitle(boolean advanced, boolean voice, String mate) {
        String what = advanced && voice ? "고급 · 보이스 대화" : advanced ? "고급 대화" : "보이스 대화";
        return what + " · " + mate;
    }
}
