package com.capsule.dialogue.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.capsule.dialogue.config.LlmProperties;
import com.capsule.dialogue.domain.LlmUsage;
import com.capsule.dialogue.dto.LabConfig;
import com.capsule.dialogue.dto.LlmContext;
import com.capsule.dialogue.dto.LlmPurpose;
import com.capsule.dialogue.dto.ProviderRequest;
import com.capsule.dialogue.engine.Llm;
import com.capsule.dialogue.repository.LlmUsageRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 게이트웨이의 공급자 선택·재시도·대체 공급자·거절·기록 규칙. 공급자는 대본대로 답하는 가짜, Spring·DB 없음 */
class LlmGatewayTest {

    static final Llm.Result OK = new Llm.Result("답", new Llm.Usage(10, 0, 0, 5), null, 3, "end_turn", false);

    /** script 순서대로 결과를 주거나 예외를 던진다 */
    static class Scripted implements LlmProvider {
        final String name;
        final Deque<Object> script = new ArrayDeque<>();
        final List<ProviderRequest> requests = new ArrayList<>();

        Scripted(String name, Object... steps) {
            this.name = name;
            script.addAll(List.of(steps));
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean ready() {
            return true;
        }

        @Override
        public Llm.Result complete(ProviderRequest request, Consumer<String> onText) {
            requests.add(request);
            Object next = script.poll();
            if (next instanceof RuntimeException e) throw e;
            return (Llm.Result) next;
        }
    }

    final LlmUsageRepository usages = mock(LlmUsageRepository.class);

    LlmGateway gateway(String fallback, boolean logContent, LlmProvider... providers) {
        LlmProperties props = new LlmProperties(
                "anthropic",
                fallback,
                Duration.ofSeconds(5),
                1,
                logContent,
                new LlmProperties.Provider("", "k", "v", Map.of("smart", "claude-x"), null),
                new LlmProperties.Provider("", "k", null, Map.of("smart", "gpt-x"), "minimal"),
                new LlmProperties.Provider("", "k", null, Map.of("smart", "jev-x"), null),
                Map.of(
                        LlmPurpose.CHAT,
                        new LlmProperties.Route("smart", 100, null, null),
                        LlmPurpose.DECIDE,
                        new LlmProperties.Route("smart", 100, null, "jev")),
                Map.of(),
                new LlmProperties.Lab(false, "", 1));
        return new LlmGateway(List.of(providers), props, usages, Clock.systemUTC());
    }

    Llm.Result call(LlmGateway g, LlmContext ctx) {
        return g.complete(
                LlmPurpose.CHAT,
                List.of(new Llm.SystemBlock("규칙", true)),
                List.of(new Llm.ChatTurn("user", "안녕")),
                ctx,
                null);
    }

    List<LlmUsage> recorded(int times) {
        ArgumentCaptor<LlmUsage> saved = ArgumentCaptor.forClass(LlmUsage.class);
        verify(usages, times(times)).save(saved.capture());
        return saved.getAllValues();
    }

    @Test
    void retriesRetryableErrorThenRecordsBoth() {
        Scripted anthropic = new Scripted("anthropic", LlmCallException.http("anthropic", 529, "overloaded"), OK);

        Llm.Result r = call(gateway(null, false, anthropic), LlmContext.app(1, 2, null));

        assertThat(r.text()).isEqualTo("답");
        assertThat(anthropic.requests).hasSize(2);
        assertThat(anthropic.requests.getFirst().model()).isEqualTo("claude-x");
        List<LlmUsage> rows = recorded(2);
        assertThat(rows.get(0).status()).isEqualTo(LlmUsage.ERROR);
        assertThat(rows.get(1).status()).isEqualTo(LlmUsage.OK);
        assertThat(rows.get(1).request()).isNull(); // 서비스 호출은 원문을 남기지 않음 (log-content=false)
    }

    @Test
    void fallsBackToOtherProviderWithItsOwnModel() {
        Scripted anthropic = new Scripted("anthropic", LlmCallException.http("anthropic", 401, "bad key"));
        Scripted openai = new Scripted("openai", OK);

        call(gateway("openai", false, anthropic, openai), LlmContext.app(1, 2, null));

        assertThat(anthropic.requests).hasSize(1); // 401은 재시도하지 않음
        assertThat(openai.requests.getFirst().model()).isEqualTo("gpt-x");
        assertThat(openai.requests.getFirst().reasoningEffort()).isEqualTo("minimal");
        assertThat(recorded(2)).extracting(LlmUsage::provider).containsExactly("anthropic", "openai");
    }

    @Test
    void refusalIsNotRetriedOrPassedOn() {
        Scripted anthropic =
                new Scripted("anthropic", new Llm.Result("", Llm.Usage.ZERO, null, 1, LlmCallException.REFUSAL, false));
        Scripted openai = new Scripted("openai", OK);

        assertThatThrownBy(() -> call(gateway("openai", false, anthropic, openai), LlmContext.app(1, 2, null)))
                .isInstanceOfSatisfying(
                        LlmCallException.class, e -> assertThat(e.refusal()).isTrue());
        assertThat(openai.requests).isEmpty();
        assertThat(recorded(1).getFirst().error()).isEqualTo(LlmCallException.REFUSAL);
    }

    @Test
    void labPinsProviderAndModelWithoutFallbackAndKeepsContent() {
        Scripted anthropic = new Scripted("anthropic", LlmCallException.http("anthropic", 400, "bad"));
        Scripted openai = new Scripted("openai", OK);
        LabConfig pinned = new LabConfig(
                null, null, null, null, "anthropic", "claude-pinned", 0.2, 50, null, null, null, null, null, null);

        assertThatThrownBy(
                        () -> call(gateway("openai", false, anthropic, openai), LlmContext.lab(LlmContext.LAB, pinned)))
                .isInstanceOf(LlmCallException.class);
        assertThat(openai.requests).isEmpty(); // 실험 조건을 고정했으면 대체하지 않는다
        ProviderRequest sent = anthropic.requests.getFirst();
        assertThat(sent.model()).isEqualTo("claude-pinned");
        assertThat(sent.temperature()).isEqualTo(0.2);
        assertThat(sent.maxTokens()).isEqualTo(50);
        assertThat(recorded(1).getFirst().request()).contains("claude-pinned"); // 실험실은 원문을 남긴다
    }

    @Test
    void purposeProviderFallsBackToDefaultProvider() {
        Scripted jev = new Scripted("jev", LlmCallException.http("jev", 400, "bad"));
        Scripted anthropic = new Scripted("anthropic", OK);

        // 결정은 Jev로, Jev가 실패하면 기본 공급자(LLM)가 같은 질문에 답한다
        Llm.Result r = gateway(null, false, jev, anthropic)
                .complete(
                        LlmPurpose.DECIDE,
                        List.of(),
                        List.of(new Llm.ChatTurn("user", "{}")),
                        LlmContext.app(1, 2, null),
                        null);

        assertThat(r.text()).isEqualTo("답");
        assertThat(jev.requests.getFirst().model()).isEqualTo("jev-x");
        assertThat(anthropic.requests.getFirst().model()).isEqualTo("claude-x");
        assertThat(recorded(2)).extracting(LlmUsage::provider).containsExactly("jev", "anthropic");
    }

    @Test
    void noNewCallAfterTheServiceDeadline() {
        Scripted anthropic = new Scripted("anthropic", LlmCallException.http("anthropic", 529, "overloaded"), OK);
        Scripted openai = new Scripted("openai", OK);

        // 이미 지난 마감: 첫 호출도 시작하지 않는다 (서비스는 대체 대사로 넘어갔음)
        assertThatThrownBy(() -> call(
                        gateway("openai", false, anthropic, openai),
                        LlmContext.app(1, 2, java.time.Instant.now().minusSeconds(1))))
                .isInstanceOf(LlmCallException.class);
        assertThat(anthropic.requests).isEmpty();
        assertThat(openai.requests).isEmpty();
        verify(usages, times(0)).save(org.mockito.ArgumentMatchers.any());
    }
}
