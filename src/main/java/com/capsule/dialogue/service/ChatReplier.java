package com.capsule.dialogue.service;

import com.capsule.dialogue.client.Decider;
import com.capsule.dialogue.client.Decider.Decision;
import com.capsule.dialogue.config.DialogueProperties;
import com.capsule.dialogue.domain.ChatMessage;
import com.capsule.dialogue.dto.ChatInput;
import com.capsule.dialogue.dto.ChatLine;
import com.capsule.dialogue.dto.ChatOutcome;
import com.capsule.dialogue.dto.LlmContext;
import com.capsule.relation.api.RelationTypes.ChatSignal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 답장 한 턴 (명세 20): 답장 파이프라인(글)과 사용자 말 신호 읽기(결정)를 동시에 부른다. 서비스·실험실·평가가 같이 쓴다.
 * 원칙: LLM은 고르기만 하고 숫자는 코드가 정한다 (ADR 0017). 신호 → 점수는 relation이 한다.
 * 신호는 답장을 늦추지 않는다: 답장이 끝났을 때 마감이 지났으면 기다리지 않고 NEUTRAL(0점).
 */
@Component
public class ChatReplier {

    private static final Logger log = LoggerFactory.getLogger(ChatReplier.class);
    private static final ExecutorService THREADS = Executors.newVirtualThreadPerTaskExecutor();
    // TODO(judgment): J-05 신호를 읽을 때 같이 보여 줄 앞 대화 수
    static final int CONTEXT_LINES = 4;
    public static final String QUESTION = "signal";

    private final Decider decider;
    private final DialogueProperties props;
    private final Clock clock;

    ChatReplier(Decider decider, DialogueProperties props, Clock clock) {
        this.decider = decider;
        this.props = props;
        this.clock = clock;
    }

    public ChatOutcome reply(ChatPipeline pipeline, ChatInput in, LlmContext ctx) {
        CompletableFuture<Decision> signal = CompletableFuture.supplyAsync(() -> read(in, ctx), THREADS);
        ChatOutcome outcome;
        try {
            outcome = pipeline.reply(in, ctx);
        } catch (RuntimeException e) {
            signal.cancel(true);
            throw e;
        }
        Decision d = await(signal, ctx.deadline());
        // TODO(judgment): J-06 확신이 낮으면 0점. 기준값은 실험실 평가로 정한다
        if (d == null || d.confidence() < props.signalMinConfidence())
            return outcome.withSignal(ChatSignal.NEUTRAL, d == null ? null : d.confidence());
        return outcome.withSignal(ChatSignal.valueOf(d.choice()), d.confidence());
    }

    private Decision read(ChatInput in, LlmContext ctx) {
        try {
            return decider.decide(state(in), Map.of(QUESTION, question()), ctx).get(QUESTION);
        } catch (RuntimeException e) {
            log.warn("신호 읽기 실패 → NEUTRAL: {}", e.toString());
            return null;
        }
    }

    private Decision await(CompletableFuture<Decision> f, Instant deadline) {
        try {
            if (deadline == null) return f.join(); // 실험실·평가: 늦어도 끝까지
            long left = Duration.between(Instant.now(clock), deadline).toMillis();
            return f.get(Math.max(0, left), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    static String state(ChatInput in) {
        List<ChatLine> h = in.history();
        String recent = h.subList(Math.max(0, h.size() - CONTEXT_LINES), h.size()).stream()
                .map(l -> (ChatMessage.USER.equals(l.sender())
                                ? "사용자: "
                                : in.character().name() + ": ")
                        + l.content())
                .collect(Collectors.joining("\n"));
        return "캐릭터: " + in.character().name() + "\n"
                + "앞 대화 (맥락으로만 본다):\n" + (recent.isEmpty() ? "없음" : recent) + "\n"
                + "사용자의 마지막 말: " + in.userMessage();
    }

    static Decider.Question question() {
        Map<String, String> criteria = new LinkedHashMap<>();
        Arrays.stream(ChatSignal.values()).forEach(s -> criteria.put(s.name(), s.meaning()));
        return new Decider.Question("사용자의 마지막 말이 캐릭터와의 관계에 주는 신호를 하나 고른다", criteria);
    }
}
