package com.capsule.dialogue.service;

import com.capsule.common.settings.RuntimeSettings;
import com.capsule.dialogue.client.Decider;
import com.capsule.dialogue.client.Decider.Decision;
import com.capsule.dialogue.config.DialogueProperties;
import com.capsule.dialogue.dto.ChatInput;
import com.capsule.dialogue.dto.ChatOutcome;
import com.capsule.dialogue.dto.LlmContext;
import com.capsule.relation.api.RelationTypes.SignalType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 답장 한 턴 (명세 20): 답장 파이프라인(글)과 사용자 말의 종류 고르기. 서비스·실험실·평가가 같이 쓴다.
 * 원칙: LLM은 고르기만 하고 숫자는 코드가 정한다 (ADR 0022).
 * 종류는 결정 모델이 문장 뜻을 보고 고른다 (명세 20, 2026-11-07 말 패턴 폐기). 답장과 동시에 부르고,
 * 답장을 늦추지 않는다: 답장이 끝났을 때 마감이 지났거나 실패 · 낮은 확신이면 NEUTRAL(0점).
 */
@Component
@RequiredArgsConstructor
public class ChatReplier {

    private static final Logger log = LoggerFactory.getLogger(ChatReplier.class);
    private static final ExecutorService THREADS = Executors.newVirtualThreadPerTaskExecutor();
    public static final String QUESTION = "signal";

    private final Decider decider;
    private final RuntimeSettings<DialogueProperties> props;
    private final Clock clock;

    public ChatOutcome reply(ChatPipeline pipeline, ChatInput in, LlmContext ctx) {
        return reply(pipeline, in, ctx, null);
    }

    /** @param onText 답장 LLM 원문 스트림 (명세 28). null이면 한 번에 */
    public ChatOutcome reply(ChatPipeline pipeline, ChatInput in, LlmContext ctx, Consumer<String> onText) {
        CompletableFuture<Decision> asked = CompletableFuture.supplyAsync(() -> read(in, question(), ctx), THREADS);
        ChatOutcome outcome;
        try {
            outcome = pipeline.reply(in, ctx, onText);
        } catch (RuntimeException e) {
            asked.cancel(true);
            throw e;
        }
        Decision d = await(asked, ctx.deadline());
        Double confidence = d == null ? null : d.confidence();
        // TODO(judgment): J-06 확신이 낮으면 0점. 기준값은 실험실 평가로 정한다
        if (d == null || d.confidence() < props.current().signalMinConfidence())
            return outcome.withSignal(SignalType.NEUTRAL, confidence);
        SignalType chosen = SignalType.valueOf(d.choice());
        // 비율 감점(−30%)이라 오탐이 비싸다: 더 높은 확신이 아니면 무례로
        if (chosen == SignalType.HATE_OR_SEXUAL
                && d.confidence() < props.current().severeMinConfidence()) chosen = SignalType.RUDE;
        return outcome.withSignal(chosen, confidence);
    }

    private Decision read(ChatInput in, Decider.Question q, LlmContext ctx) {
        try {
            return decider.decide(state(in, props.current().signalContextLines()), Map.of(QUESTION, q), ctx)
                    .get(QUESTION);
        } catch (RuntimeException e) {
            log.warn("말의 종류 고르기 실패: {}", e.toString());
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

    /** @param contextLines 같이 보여 줄 앞 대화 수 (signal-context-lines) */
    // TODO(judgment): J-05 결정 모델에 같이 보여 줄 앞 대화 수
    public static String state(ChatInput in, int contextLines) {
        String recent = PromptVars.transcript(
                PromptVars.tail(in.history(), contextLines), in.character().name());
        return "캐릭터: " + in.character().name() + "\n"
                + "앞 대화 (맥락으로만 본다):\n" + PromptVars.orNone(recent, "없음") + "\n"
                + "사용자의 마지막 말: " + in.userMessage();
    }

    /** 모든 종류 중 하나 (보기 설명 = SignalType.meaning) */
    public static Decider.Question question() {
        Map<String, String> criteria = new LinkedHashMap<>();
        Arrays.stream(SignalType.values()).forEach(s -> criteria.put(s.name(), s.meaning()));
        return new Decider.Question("사용자의 마지막 말이 어떤 종류의 말인지 하나 고른다", criteria);
    }
}
