package com.capsule.dialogue.internal;

import static com.capsule.dialogue.internal.ChatMessage.CHARACTER;
import static com.capsule.dialogue.internal.ChatMessage.USER;

import com.capsule.common.error.ApiException;
import com.capsule.common.ratelimit.RateLimiter;
import com.capsule.content.api.CharacterQuery;
import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.content.api.ContentTypes.ExpressionRef;
import com.capsule.content.api.ContentTypes.MotionRef;
import com.capsule.content.api.ContentTypes.Reaction;
import com.capsule.content.api.ReactionQuery;
import com.capsule.dialogue.internal.llm.LlmClient;
import com.capsule.dialogue.internal.llm.LlmClient.Line;
import com.capsule.dialogue.internal.llm.LlmClient.Reply;
import com.capsule.dialogue.internal.llm.LlmClient.ReplyContext;
import com.capsule.relation.api.RelationCommands;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.api.RelationTypes.AffinityChange;
import com.capsule.relation.api.RelationTypes.RelationView;
import com.capsule.relation.api.RelationTypes.SessionEventRef;
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
class DialogueService {

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

    record ReactionResponse(
            UUID requestId,
            String trigger,
            String content,
            MotionRef motion,
            ExpressionRef expression,
            AffinityChange affinity,
            boolean fallback,
            String audioBase64) {}

    record MessageResponse(
            UUID requestId,
            String content,
            MotionRef motion,
            ExpressionRef expression,
            AffinityChange affinity,
            boolean fallback,
            String audioBase64) {}

    ReactionResponse react(long userId, long sessionId, long eventId, UUID requestId, Integer intensity, Boolean tts) {
        SessionInfo session = openSession(userId, sessionId);
        SessionEventInfo event = sessions.findEvent(sessionId, eventId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND"));
        long characterId = session.characterId();
        String trigger = triggerOf(event);
        Integer awayLevel = trigger.equals("AWAY") ? (intensity == null ? 1 : intensity) : null;

        Reaction reaction = reactions.pick(characterId, trigger, awayLevel);
        // 호감도는 상태 변화 전송(18) 때 이미 반영됨. 여기서는 읽기만
        AffinityChange affinity = relationQuery.changeOf(new SessionEventRef(
                userId, characterId, sessionId, eventId, event.type(), event.firstFocusOfSession()));
        CharacterInfo character = character(characterId);
        Optional<String> line = callLlm(() -> llm.reactionLine(character, trigger, awayLevel));

        return new ReactionResponse(
                requestId,
                trigger,
                line.orElseGet(() -> LlmClient.FIXED_REACTION_LINES.get(trigger)),
                reaction.motion(),
                reaction.expression(),
                affinity,
                line.isEmpty(),
                audio(tts));
    }

    // ponytail: LLM을 기다리는 동안(최대 5초) DB 커넥션을 잡고 있음. 동시 사용자가 많아지면 LLM 호출을 트랜잭션 밖으로 뺄 것
    // ponytail: 같은 requestId가 동시에 두 번 오면 두 번째는 UNIQUE 위반(500). 필요하면 잡아서 저장된 답장을 다시 읽기
    @Transactional
    MessageResponse send(long userId, long sessionId, UUID requestId, String content, Boolean tts) {
        SessionInfo session = openSession(userId, sessionId);
        long characterId = session.characterId();
        Conversation conversation = conversation(userId, characterId);

        // 재전송은 한도 검사 전에 돌려준다: 응답을 못 받아 다시 보낸 요청이 429가 되면 안 됨
        Optional<ChatMessage> answered =
                messages.findByConversationIdAndRequestIdAndSender(conversation.id(), requestId, CHARACTER);
        if (answered.isPresent()) {
            // ponytail: 재전송 때 원래 delta(와 모션·표정·fallback)는 다시 보여주지 않음. 필요하면 chat_messages에 같이 저장
            return new MessageResponse(
                    requestId, answered.get().content(), null, null, unchanged(userId, characterId), false, audio(tts));
        }
        if (!rateLimiter.tryAcquire("rate:chat:" + userId, MESSAGES_PER_MINUTE, Duration.ofMinutes(1))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS");
        }

        List<Line> history = lines(messages.findByConversationIdAndSessionIdOrderById(conversation.id(), sessionId));
        messages.save(new ChatMessage(conversation.id(), sessionId, requestId, USER, content, Instant.now(clock)));
        ReplyContext context = new ReplyContext(
                character(characterId),
                conversation.summary(),
                history,
                memories.memoriesForConversation(userId),
                content);
        Optional<Reply> reply = callLlm(() -> llm.reply(context));
        String answer = reply.map(Reply::content).orElse(LlmClient.FALLBACK_REPLY);
        messages.save(new ChatMessage(conversation.id(), sessionId, requestId, CHARACTER, answer, Instant.now(clock)));

        // 명세 20: fallback이면 호감도 변화 없음
        AffinityChange affinity = reply.map(
                        r -> relationCommands.applyChat(userId, characterId, sessionId, requestId, r.affinityScore()))
                .orElseGet(() -> unchanged(userId, characterId));
        // TODO(judgment): J-07 답장 감정(Reply.emotion) → 모션·표정 고르는 규칙. 지금은 없음
        Reaction reaction = Reaction.NONE;
        return new MessageResponse(
                requestId, answer, reaction.motion(), reaction.expression(), affinity, reply.isEmpty(), audio(tts));
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

    // ponytail: find 후 save. 같은 사용자의 첫 대화가 동시에 오면 UNIQUE 위반 가능. 문제 되면 INSERT ... ON CONFLICT DO NOTHING
    private Conversation conversation(long userId, long characterId) {
        return conversations
                .findByUserIdAndCharacterId(userId, characterId)
                .orElseGet(() -> conversations.save(new Conversation(userId, characterId, Instant.now(clock))));
    }

    private AffinityChange unchanged(long userId, long characterId) {
        RelationView view = relationQuery.view(userId, characterId);
        return new AffinityChange(0, view.points(), view.level(), false);
    }

    private static List<Line> lines(List<ChatMessage> chat) {
        return chat.stream().map(m -> new Line(m.sender(), m.content())).toList();
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
