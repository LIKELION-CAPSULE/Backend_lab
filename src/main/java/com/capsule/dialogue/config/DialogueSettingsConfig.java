package com.capsule.dialogue.config;

import static com.capsule.common.settings.RuntimeSettings.require;

import com.capsule.common.settings.RuntimeSettings;
import com.capsule.dialogue.dto.LlmPurpose;
import com.capsule.dialogue.service.ChatPipeline;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실험실 '운영 설정'에서 바꿀 수 있는 대화 · LLM 설정 (ADR 0026). 저장하면 다음 호출부터 서비스에 들어간다.
 * 열지 않는 칸: 시작할 때만 읽는 것(캐시 크기 · 시간, HTTP 한도), 바꾸면 위험한 것(API 키 · 공급자 주소 · 버전 헤더),
 * 개인정보 결정(log-content: 실제 사용자 대화 원문 저장), 실험실 자체(lab).
 */
@Configuration
class DialogueSettingsConfig {

    static final String TABLE = "dialogue_settings";

    /** 새 칸을 열 때는 그 값을 매번 읽는지(시작할 때 한 번 읽어 들고 있지 않은지) 먼저 본다 */
    static final List<String> LLM_PATHS = List.of(
            "provider",
            "fallbackProvider",
            "maxRetries",
            "retryBackoff",
            "routes",
            "anthropic.models",
            "anthropic.thinking",
            "openai.models",
            "openai.reasoningEffort",
            "jev.models",
            "prices");

    static final Set<String> PROVIDERS = Set.of("fake", "anthropic", "openai");
    static final Set<String> THINKING_TYPES = Set.of("disabled", "between_tools", "adaptive");
    static final Set<String> EFFORTS = Set.of("low", "medium", "high", "xhigh", "max");

    @Bean
    RuntimeSettings<DialogueProperties> dialogueSettings(JdbcTemplate jdbc, JsonMapper json, DialogueProperties yml) {
        return RuntimeSettings.stored(
                jdbc,
                json,
                TABLE,
                "dialogue",
                yml,
                DialogueProperties.class,
                RuntimeSettings.allExcept(DialogueProperties.class, "contentCacheTtl", "contentMaxCursors"),
                DialogueSettingsConfig::checkDialogue);
    }

    @Bean
    RuntimeSettings<LlmProperties> llmSettings(JdbcTemplate jdbc, JsonMapper json, LlmProperties yml) {
        return RuntimeSettings.stored(
                jdbc, json, TABLE, "llm", yml, LlmProperties.class, LLM_PATHS, v -> checkLlm(v, yml));
    }

    /** 나누기 · 잘라 내기 · 기다리기가 깨지지 않을 범위만 본다. 값이 좋은지는 실험실에서 */
    static void checkDialogue(DialogueProperties v) {
        require(
                Set.of(ChatPipeline.SCENE, ChatPipeline.SIMPLE).contains(v.chatPipeline()),
                "chatPipeline",
                "SCENE · SIMPLE");
        require(
                Set.of(DialogueProperties.PATTERN, DialogueProperties.DECIDER).contains(v.signalDetector()),
                "signalDetector",
                "PATTERN · DECIDER");
        require(v.emotions() != null && !v.emotions().isEmpty(), "emotions", "하나 이상");
        require(v.reactionLlmTriggers() != null, "reactionLlmTriggers", "목록 (비우려면 [])");
        require(v.chatScores() != null, "chatScores", "표 (비우려면 {})");
        ratio(v.signalMinConfidence(), "signalMinConfidence");
        ratio(v.severeMinConfidence(), "severeMinConfidence");
        ratio(v.giveUpRatio(), "giveUpRatio");
        Map.of(
                        "chatRepeatWindow", v.chatRepeatWindow(),
                        "memoryMax", v.memoryMax(),
                        "recapMax", v.recapMax(),
                        "messageLimit", v.messageLimit(),
                        "chattyMessages", v.chattyMessages(),
                        "awayIntensityMax", v.awayIntensityMax(),
                        "historyLines", v.historyLines())
                .forEach((k, n) -> require(n >= 1, k, "1 이상"));
        Map.of(
                        "chatRepeatLimit", v.chatRepeatLimit(),
                        "reactionContextLines", v.reactionContextLines(),
                        "signalContextLines", v.signalContextLines(),
                        "lowEffortMaxChars", v.lowEffortMaxChars())
                .forEach((k, n) -> require(n >= 0, k, "0 이상"));
        Map.of(
                        "llmBudget", v.llmBudget(),
                        "inFlightWait", v.inFlightWait(),
                        "inFlightPoll", v.inFlightPoll(),
                        "messageLimitWindow", v.messageLimitWindow(),
                        "chattyWindow", v.chattyWindow(),
                        "factPickWait", v.factPickWait())
                .forEach((k, d) -> require(d != null && d.isPositive(), k, "0보다 긴 시간 (예: PT5S)"));
    }

    /**
     * 공급자 이름 · 등급 → 모델이 다 맞는지 (틀리면 호출마다 실패해서 대체 대사만 나간다).
     * yml과 다른 공급자로 바꿀 때만 키가 있는지 본다: 키 없는 로컬에서도 다른 칸은 바꿀 수 있게
     */
    static void checkLlm(LlmProperties v, LlmProperties yml) {
        require(PROVIDERS.contains(v.provider()), "provider", "fake · anthropic · openai");
        require(!"fake".equals(v.provider()) || "fake".equals(yml.provider()), "provider", "실제 LLM 서버를 가짜로 바꿀 수 없음");
        String fallback = v.fallbackProvider();
        require(
                blank(fallback)
                        || PROVIDERS.contains(fallback)
                                && (!"fake".equals(fallback) || fallback.equals(yml.fallbackProvider())),
                "fallbackProvider",
                "비움 · anthropic · openai");
        require(v.maxRetries() >= 0 && v.maxRetries() <= 3, "maxRetries", "0~3");
        require(
                v.retryBackoff() != null
                        && !v.retryBackoff().isNegative()
                        && v.retryBackoff().compareTo(Duration.ofSeconds(2)) <= 0,
                "retryBackoff",
                "0~2초 (예: PT0.3S)");
        require(
                v.routes() != null && v.routes().keySet().containsAll(Arrays.asList(LlmPurpose.values())),
                "routes",
                "용도 전부");
        v.routes().forEach((purpose, r) -> {
            String path = "routes";
            require(r.maxTokens() > 0, path, purpose + " max-tokens는 1 이상");
            require(
                    blank(r.provider()) || PROVIDERS.contains(r.provider()) || "jev".equals(r.provider()),
                    path,
                    purpose + " provider는 비움 · anthropic · openai · jev");
            Stream.of(r.provider(), v.provider(), fallback)
                    .filter(p -> !blank(p))
                    .forEach(p -> require(
                            modelOf(v, p, r) != null, path, purpose + ": " + p + "에 등급 " + r.tier() + " 모델이 없음"));
        });
        requireKey(v.provider(), yml.provider(), yml, "provider");
        requireKey(fallback, yml.fallbackProvider(), yml, "fallbackProvider");
        if (v.anthropic() != null && v.anthropic().thinking() != null)
            v.anthropic().thinking().forEach((model, t) -> {
                require(
                        blank(t.type()) || THINKING_TYPES.contains(t.type()),
                        "anthropic.thinking",
                        model + " type: " + THINKING_TYPES);
                require(
                        blank(t.effort()) || EFFORTS.contains(t.effort()),
                        "anthropic.thinking",
                        model + " effort: " + EFFORTS);
            });
    }

    /** 키는 환경변수로만 들어오므로 yml(시작 때) 값으로 본다 */
    private static void requireKey(String provider, String ymlValue, LlmProperties yml, String path) {
        if (blank(provider) || provider.equals(ymlValue)) return;
        LlmProperties.Provider s = yml.providerSettings(provider);
        require(s == null || s.hasKey(), path, provider + " API 키가 이 서버에 없음 (Railway 변수)");
    }

    private static String modelOf(LlmProperties v, String provider, LlmProperties.Route r) {
        try {
            return v.model(provider, r);
        } catch (IllegalStateException e) {
            return null;
        }
    }

    private static void ratio(double x, String path) {
        require(x >= 0 && x <= 1, path, "0~1");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
