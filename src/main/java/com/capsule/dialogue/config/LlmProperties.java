package com.capsule.dialogue.config;

import com.capsule.dialogue.dto.LlmPurpose;
import com.capsule.dialogue.engine.Llm;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 설정 (ADR 0022). 공급자를 바꾸는 것은 provider 한 줄이다: 용도(routes)는 모델 이름 대신 등급(tier)을 적고,
 * 등급을 실제 모델로 바꾸는 표는 공급자마다 따로 둔다. API 키는 환경변수로만 받는다.
 *
 * @param provider 기본 공급자: fake · anthropic · openai (jev는 결정 전용이라 routes의 provider로만)
 * @param jev TypeSafe Jev 결정 모델 (DECIDE 용도만). models의 fast = 고정할 버전
 * @param fallbackProvider 기본 공급자가 실패하면 한 번 더 시도할 공급자. 비우면 없음
 * @param httpTimeout 공급자 HTTP 한 번의 한도. 서비스의 5초 대체 대사(명세 19·20)와 별개로, 늦은 응답도 끝까지 받아 기록한다
 * @param maxRetries 429·5xx·연결 오류일 때 같은 공급자로 다시 시도할 횟수
 * @param logContent true면 서비스(APP) 호출도 프롬프트·응답 원문을 llm_usages에 남긴다. 실험실·평가는 항상 남긴다
 * @param prices 모델별 가격(달러/백만 토큰). 없으면 Llm.DEFAULT_PRICES, 거기도 없으면 0
 */
@ConfigurationProperties("capsule.llm")
public record LlmProperties(
        String provider,
        String fallbackProvider,
        Duration httpTimeout,
        int maxRetries,
        boolean logContent,
        Provider anthropic,
        Provider openai,
        Provider jev,
        Map<LlmPurpose, Route> routes,
        Map<String, Llm.Price> prices,
        Lab lab) {

    /**
     * @param models 등급 → 모델 이름 (예: fast → claude-haiku-5-5)
     * @param version Anthropic API 버전 헤더
     * @param reasoningEffort OpenAI 추론 모델의 reasoning_effort. 비우면 보내지 않는다
     * @param thinking Anthropic 모델 이름 → 생각 설정 (#92). 없는 모델에는 보내지 않는다(모델 기본값)
     */
    public record Provider(
            String baseUrl,
            String apiKey,
            String version,
            Map<String, String> models,
            String reasoningEffort,
            Map<String, Thinking> thinking) {
        public boolean hasKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        /** 이 모델의 생각 설정. 없으면 null */
        public Thinking thinkingFor(String model) {
            return thinking == null ? null : thinking.get(model);
        }
    }

    /**
     * Claude 5.5는 생각 설정이 없으면 스스로 생각하고(adaptive) 그 토큰이 max_tokens에서 빠져, 짧은 한도에서 빈 답이 난다 (#92).
     * 모델마다 받는 값이 다르다: haiku-5-5는 disabled(effort high 이하), sonnet-5-5는 disabled가 400이라 between_tools가 가장 낮은 설정.
     *
     * @param type thinking.type (disabled · between_tools · adaptive). 비우면 보내지 않는다
     * @param effort output_config.effort (low · medium · high · xhigh · max). 비우면 보내지 않는다
     */
    public record Thinking(String type, String effort) {}

    /**
     * @param temperature 비우면 보내지 않는다 (공급자 기본값)
     * @param provider 이 용도만 다른 공급자로 (예: DECIDE → jev). 비우면 기본 공급자. 실패하면 기본 공급자로 한 번 더
     */
    public record Route(String tier, int maxTokens, Double temperature, String provider) {}

    /**
     * @param enabled 실험실(/internal/llm-lab) 열기. local에서만 true
     * @param token 비우지 않으면 실험실 요청에 X-Lab-Token 헤더가 같아야 한다 (공용 개발 서버에 열 때)
     * @param evalParallelism 평가 실행에서 동시에 돌릴 케이스 수 (공급자 속도 제한에 맞춤)
     */
    public record Lab(boolean enabled, String token, int evalParallelism) {}

    public Route route(LlmPurpose purpose) {
        Route r = routes == null ? null : routes.get(purpose);
        if (r == null) throw new IllegalStateException("capsule.llm.routes에 " + purpose + "가 없음");
        return r;
    }

    public Provider providerSettings(String name) {
        return switch (name) {
            case "anthropic" -> anthropic;
            case "openai" -> openai;
            case "jev" -> jev;
            default -> null;
        };
    }

    /** 그 공급자에서 이 용도에 쓸 모델. Fake는 모델이 없다 */
    public String model(String providerName, Route route) {
        Provider p = providerSettings(providerName);
        if (p == null) return providerName;
        String model = p.models() == null ? null : p.models().get(route.tier());
        if (model == null)
            throw new IllegalStateException("capsule.llm." + providerName + ".models에 등급 " + route.tier() + "가 없음");
        return model;
    }

    public Llm.Price price(String model) {
        Llm.Price p = prices == null ? null : prices.get(model);
        return p != null ? p : Llm.DEFAULT_PRICES.get(model);
    }
}
