package com.capsule.dialogue.service;

import static com.capsule.dialogue.domain.ChatMessage.CHARACTER;
import static com.capsule.dialogue.domain.ChatMessage.USER;

import com.capsule.common.error.ApiException;
import com.capsule.common.ratelimit.RateLimiter;
import com.capsule.content.api.CharacterQuery;
import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.content.api.ContentTypes.Reaction;
import com.capsule.content.api.ReactionQuery;
import com.capsule.dialogue.client.LlmClient;
import com.capsule.dialogue.domain.ChatMessage;
import com.capsule.dialogue.domain.Conversation;
import com.capsule.dialogue.dto.ChatLine;
import com.capsule.dialogue.dto.LlmReaction;
import com.capsule.dialogue.dto.LlmReply;
import com.capsule.dialogue.dto.MessageResponse;
import com.capsule.dialogue.dto.ReactionCharacter;
import com.capsule.dialogue.dto.ReactionResponse;
import com.capsule.dialogue.dto.ReplyContext;
import com.capsule.dialogue.repository.ChatMessageRepository;
import com.capsule.dialogue.repository.ConversationRepository;
import com.capsule.relation.api.RelationCommands;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.api.RelationTypes.AffinityChange;
import com.capsule.relation.api.RelationTypes.RelationView;
import com.capsule.session.api.SessionQuery;
import com.capsule.session.api.SessionTypes.SessionClosing;
import com.capsule.session.api.SessionTypes.SessionEventInfo;
import com.capsule.session.api.SessionTypes.SessionInfo;
import com.capsule.user.api.MemoryQuery;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 이벤트 반응(api.md 19), 메시지(20), 공부 종료 때 대화 마무리(22, session이 부름) */
@Service
public class DialogueService {

    private static final Logger log = LoggerFactory.getLogger(DialogueService.class);

    /** 명세 19·20: 5초 안에 못 만들면 고정 대사 */
    private static final long LLM_TIMEOUT_SECONDS = 5;

    private static final ExecutorService LLM_THREADS = Executors.newVirtualThreadPerTaskExecutor();
    private static final int MESSAGES_PER_MINUTE = 10;

    private final SessionQuery sessions;
    private final CharacterQuery characters;
    private final ReactionQuery reactions;
    private final RelationQuery relationQuery;
    private final RelationCommands relationCommands;
    private final MemoryQuery memories;
    private final RateLimiter rateLimiter;
    private final LlmClient llm;
    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final Clock clock;

    DialogueService(
            SessionQuery sessions,
            CharacterQuery characters,
            ReactionQuery reactions,
            RelationQuery relationQuery,
            RelationCommands relationCommands,
            MemoryQuery memories,
            RateLimiter rateLimiter,
            LlmClient llm,
            ConversationRepository conversations,
            ChatMessageRepository messages,
            Clock clock) {
        this.sessions = sessions;
        this.characters = characters;
        this.reactions = reactions;
        this.relationQuery = relationQuery;
        this.relationCommands = relationCommands;
        this.memories = memories;
        this.rateLimiter = rateLimiter;
        this.llm = llm;
        this.conversations = conversations;
        this.messages = messages;
        this.clock = clock;
    }

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

        // 호감도는 상태 변화 전송(18)이 반영하고 그 응답으로 돌려준다. 여기서는 다루지 않음
        Reaction reaction = reactions.pick(characterId, trigger, awayLevel);
        CharacterInfo character = character(characterId);
        Optional<LlmReaction> generated = callLlm(() -> llm.reaction(character, trigger, awayLevel));

        // 뼈대는 세션당 캐릭터 1명
        return new ReactionResponse(
                requestId,
                List.of(new ReactionCharacter(
                        characterId,
                        generated
                                .map(LlmReaction::content)
                                .orElseGet(() -> LlmClient.FIXED_REACTION_LINES.get(trigger)),
                        generated.map(LlmReaction::emotion).orElse(null),
                        reaction.motion(),
                        reaction.expression(),
                        generated.isEmpty(),
                        audio(tts))));
    }

    // ponytail: LLM을 기다리는 동안(최대 5초) DB 커넥션을 잡고 있음. 동시 사용자가 많아지면 LLM 호출을 트랜잭션 밖으로 뺄 것
    @Transactional
    public MessageResponse send(
            long userId, long sessionId, UUID requestId, long characterId, String content, Boolean tts) {
        requireSessionCharacter(openSession(userId, sessionId), characterId);
        Conversation conversation = conversation(userId, characterId);

        // 재전송은 한도 검사 전에 돌려준다: 응답을 못 받아 다시 보낸 요청이 429가 되면 안 됨
        Optional<MessageResponse> answered = answered(conversation.id(), userId, characterId, requestId, tts);
        if (answered.isPresent()) {
            return answered.get();
        }
        if (!rateLimiter.tryAcquire("rate:chat:" + userId, MESSAGES_PER_MINUTE, Duration.ofMinutes(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS");
        }

        List<ChatLine> history =
                lines(messages.findByConversationIdAndSessionIdOrderById(conversation.id(), sessionId));
        messages.save(new ChatMessage(conversation.id(), sessionId, requestId, USER, content, Instant.now(clock)));
        ReplyContext context = new ReplyContext(
                character(characterId),
                conversation.summary(),
                history,
                memories.memoriesForConversation(userId),
                content);
        Optional<LlmReply> reply = callLlm(() -> llm.reply(context));
        String answer = reply.map(LlmReply::content).orElse(LlmClient.FALLBACK_REPLY);
        messages.save(new ChatMessage(conversation.id(), sessionId, requestId, CHARACTER, answer, Instant.now(clock)));

        // 명세 20: fallback이면 호감도 변화 없음
        AffinityChange affinity = reply.map(
                        r -> relationCommands.applyChat(userId, characterId, sessionId, requestId, r.affinityScore()))
                .orElseGet(() -> unchanged(userId, characterId));
        // TODO(judgment): J-07 답장 감정(LlmReply.emotion) → 모션·표정 고르는 규칙. 지금은 없음
        Reaction reaction = Reaction.NONE;
        return new MessageResponse(
                requestId,
                characterId,
                answer,
                reply.map(LlmReply::emotion).orElse(null),
                reaction.motion(),
                reaction.expression(),
                affinity,
                reply.isEmpty(),
                audio(tts));
    }

    /** 명세 20: 대화할 캐릭터는 그 세션 캐릭터만 (뼈대는 세션당 1명) */
    private static void requireSessionCharacter(SessionInfo session, long characterId) {
        if (session.characterId() != characterId) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CHARACTER_NOT_IN_SESSION");
        }
    }

    /**
     * 같은 requestId가 동시에 두 번 와서 늦은 쪽이 UNIQUE에 막혔을 때(먼저 온 요청이 커밋될 때까지 기다린 뒤라 답장이 있음).
     * 막힌 트랜잭션은 이미 끝났으므로 새 트랜잭션에서 읽는다
     */
    @Transactional(readOnly = true)
    public Optional<MessageResponse> answeredAfterConflict(
            long userId, long sessionId, UUID requestId, long characterId, Boolean tts) {
        requireSessionCharacter(openSession(userId, sessionId), characterId);
        return conversations
                .findByUserIdAndCharacterId(userId, characterId)
                .flatMap(c -> answered(c.id(), userId, characterId, requestId, tts));
    }

    /** 이미 답한 requestId면 저장된 답장 */
    private Optional<MessageResponse> answered(
            long conversationId, long userId, long characterId, UUID requestId, Boolean tts) {
        // ponytail: 재전송 때 원래 delta(와 모션·표정·fallback)는 다시 보여주지 않음. 필요하면 chat_messages에 같이 저장
        return messages.findByConversationIdAndRequestIdAndSender(conversationId, requestId, CHARACTER)
                .map(m -> new MessageResponse(
                        requestId,
                        characterId,
                        m.content(),
                        null,
                        null,
                        null,
                        unchanged(userId, characterId),
                        false,
                        audio(tts)));
    }

    /**
     * 공부 종료 트랜잭션 안에서 불린다. 이번 세션 대화를 요약에 합치고 메시지를 지운 뒤 완료 대사를 돌려준다.
     * LLM이 실패하거나 5초를 넘겨도 공부 종료는 성공해야 하므로 고정 문구로 대신한다.
     */
    @Transactional
    public String close(SessionClosing closing) {
        Conversation conversation = conversation(closing.userId(), closing.characterId());
        List<ChatMessage> sessionMessages =
                messages.findByConversationIdAndSessionIdOrderById(conversation.id(), closing.sessionId());
        Optional<String> summary =
                callLlm(() -> llm.mergeSummary(conversation.summary(), closing.sessionId(), lines(sessionMessages)));
        if (summary.isPresent()) {
            conversation.updateSummary(summary.get(), Instant.now(clock));
            messages.deleteAllInBatch(sessionMessages);
        }
        // ponytail: 요약에 실패하면 기존 요약을 두고 이번 세션 메시지도 남긴다(다음 종료 때는 그 세션만 보므로 다시 합쳐지지 않음).
        //   실제 LLM을 붙인 뒤 실패가 잦으면 남은 메시지를 합치는 재시도 배치
        return callLlm(() -> llm.closingLine(closing.characterName())).orElse(LlmClient.FALLBACK_CLOSING_LINE);
    }

    /** 세션 첫 FOCUS는 GREETING, 그 밖의 FOCUS는 BREAK_END. 나머지는 이벤트 타입 그대로 */
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

    /** 처음 대화면 만든다. 같은 사용자의 첫 대화가 동시에 와도 ON CONFLICT로 한 행만 */
    private Conversation conversation(long userId, long characterId) {
        return conversations.findByUserIdAndCharacterId(userId, characterId).orElseGet(() -> {
            conversations.insertIfAbsent(userId, characterId, Instant.now(clock));
            return conversations.findByUserIdAndCharacterId(userId, characterId).orElseThrow();
        });
    }

    private AffinityChange unchanged(long userId, long characterId) {
        RelationView view = relationQuery.view(userId, characterId);
        return new AffinityChange(0, view.points(), view.level(), false);
    }

    private static List<ChatLine> lines(List<ChatMessage> chat) {
        return chat.stream().map(m -> new ChatLine(m.sender(), m.content())).toList();
    }

    /** 5초 안에 끝나지 않거나 실패하면 빈 값 → 호출한 쪽이 고정 대사를 쓴다 */
    private static <T> Optional<T> callLlm(Supplier<T> call) {
        try {
            return Optional.ofNullable(CompletableFuture.supplyAsync(call, LLM_THREADS)
                    .orTimeout(LLM_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .join());
        } catch (CompletionException e) {
            log.warn("LLM 호출 실패, 고정 대사로 대체", e.getCause());
            return Optional.empty();
        }
    }

    // TODO(judgment): J-10 TTS 대사 음성(mp3) 생성. 지금은 tts:true여도 null
    private static String audio(Boolean tts) {
        return null;
    }
}
