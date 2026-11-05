package com.capsule.dialogue.service;

import com.capsule.common.settings.RuntimeSettings;
import com.capsule.dialogue.client.Decider;
import com.capsule.dialogue.client.Decider.Decision;
import com.capsule.dialogue.config.DialogueProperties;
import com.capsule.dialogue.domain.ChatMessage;
import com.capsule.dialogue.dto.ChatInput;
import com.capsule.dialogue.dto.ChatLine;
import com.capsule.dialogue.dto.ChatOutcome;
import com.capsule.dialogue.dto.LlmContext;
import com.capsule.relation.api.RelationTypes.SignalType;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 답장 한 턴 (명세 20): 답장 파이프라인(글)과 사용자 말의 종류 고르기. 서비스·실험실·평가가 같이 쓴다.
 * 원칙: LLM은 고르기만 하고 숫자는 코드가 정한다 (ADR 0022).
 * 종류는 A안(말 패턴, PM 확정)이 기본이다. 결정 모델은 두 경우만 답장과 동시에 부른다:
 *   ① 패턴이 혐오 · 성적이라고 할 때 확인 (비율 감점이라 오탐이면 RUDE로) ② signal-detector: DECIDER(B안)
 * 결정은 답장을 늦추지 않는다: 답장이 끝났을 때 마감이 지났으면 기다리지 않는다 (① RUDE, ② NEUTRAL).
 */
@Component
public class ChatReplier {

    private static final Logger log = LoggerFactory.getLogger(ChatReplier.class);
    private static final ExecutorService THREADS = Executors.newVirtualThreadPerTaskExecutor();
    public static final String QUESTION = "signal";

    private final Decider decider;
    private final RuntimeSettings<DialogueProperties> props;
    private final Clock clock;

    ChatReplier(Decider decider, RuntimeSettings<DialogueProperties> props, Clock clock) {
        this.decider = decider;
        this.props = props;
        this.clock = clock;
    }

    public ChatOutcome reply(ChatPipeline pipeline, ChatInput in, LlmContext ctx) {
        return reply(pipeline, in, ctx, null);
    }

    /** @param onText 답장 LLM 원문 스트림 (명세 28). null이면 한 번에 */
    public ChatOutcome reply(ChatPipeline pipeline, ChatInput in, LlmContext ctx, Consumer<String> onText) {
        SignalType guess = ChatScorer.detect(in.userMessage(), props.current().lowEffortMaxChars());
        boolean decide = DialogueProperties.DECIDER.equals(props.current().signalDetector());
        boolean confirm = !decide && guess == SignalType.HATE_OR_SEXUAL;
        CompletableFuture<Decision> asked = decide || confirm
                ? CompletableFuture.supplyAsync(() -> read(in, decide ? question() : severeQuestion(), ctx), THREADS)
                : null;
        ChatOutcome outcome;
        try {
            outcome = pipeline.reply(in, ctx, onText);
        } catch (RuntimeException e) {
            if (asked != null) asked.cancel(true);
            throw e;
        }
        if (asked == null) return outcome.withSignal(guess, null);
        Decision d = await(asked, ctx.deadline());
        Double confidence = d == null ? null : d.confidence();
        if (confirm) {
            boolean severe = d != null
                    && SignalType.HATE_OR_SEXUAL.name().equals(d.choice())
                    && d.confidence() >= props.current().severeMinConfidence();
            return outcome.withSignal(severe ? SignalType.HATE_OR_SEXUAL : SignalType.RUDE, confidence);
        }
        // TODO(judgment): J-06 B안: 확신이 낮으면 0점. 기준값은 실험실 평가로 정한다
        if (d == null || d.confidence() < props.current().signalMinConfidence())
            return outcome.withSignal(SignalType.NEUTRAL, confidence);
        SignalType chosen = SignalType.valueOf(d.choice());
        // 비율 감점은 A안과 같은 확신 기준을 넘어야 한다
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
    static String state(ChatInput in, int contextLines) {
        List<ChatLine> h = in.history();
        String recent = h.subList(Math.max(0, h.size() - contextLines), h.size()).stream()
                .map(l -> (ChatMessage.USER.equals(l.sender())
                                ? "사용자: "
                                : in.character().name() + ": ")
                        + l.content())
                .collect(Collectors.joining("\n"));
        return "캐릭터: " + in.character().name() + "\n"
                + "앞 대화 (맥락으로만 본다):\n" + (recent.isEmpty() ? "없음" : recent) + "\n"
                + "사용자의 마지막 말: " + in.userMessage();
    }

    /** B안: 모든 종류 중 하나 */
    static Decider.Question question() {
        Map<String, String> criteria = new LinkedHashMap<>();
        Arrays.stream(SignalType.values()).forEach(s -> criteria.put(s.name(), s.meaning()));
        return new Decider.Question("사용자의 마지막 말이 어떤 종류의 말인지 하나 고른다", criteria);
    }

    /** 심한 말 확인: 패턴이 걸린 말이 정말 혐오 · 성적인지 */
    static Decider.Question severeQuestion() {
        Map<String, String> criteria = new LinkedHashMap<>();
        criteria.put(SignalType.HATE_OR_SEXUAL.name(), SignalType.HATE_OR_SEXUAL.meaning());
        criteria.put(SignalType.RUDE.name(), "혐오 · 성적인 말은 아니다 (욕, 농담, 힘들다는 표현, 남의 말 인용 등)");
        return new Decider.Question("사용자의 마지막 말이 캐릭터를 향한 혐오 표현이나 성적인 말인지 고른다", criteria);
    }
}
