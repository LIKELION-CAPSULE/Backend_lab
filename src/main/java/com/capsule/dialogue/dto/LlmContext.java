package com.capsule.dialogue.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * LLM 호출에 붙는 꼬리표와 실험용 덮어쓰기. 꼬리표는 llm_usages에 그대로 남는다.
 *
 * @param source APP(서비스) · LAB(실험실) · EVAL(평가)
 * @param traceId 한 요청(대화 한 턴 등)에서 나간 호출을 묶는 값
 * @param promptVersionIds 이 호출에 쓴 prompt_versions.id
 * @param provider · model · temperature · maxTokens 실험실·평가에서만 채운다. null이면 설정(capsule.llm) 값
 * @param deadline 서비스가 기다리는 마감(명세의 5초). 지나면 게이트웨이가 새 호출(재시도·대체 공급자)을
 *     시작하지 않는다: 결과가 버려질 호출에 돈을 쓰지 않게. null이면 마감 없음(실험실은 늦어도 끝까지 받아 실제 지연을 본다)
 */
public record LlmContext(
        String source,
        UUID traceId,
        Long userId,
        Long sessionId,
        List<Long> promptVersionIds,
        String provider,
        String model,
        Double temperature,
        Integer maxTokens,
        Instant deadline) {

    public static final String APP = "APP";
    public static final String LAB = "LAB";
    public static final String EVAL = "EVAL";

    public static LlmContext app(long userId, long sessionId, Instant deadline) {
        return new LlmContext(APP, UUID.randomUUID(), userId, sessionId, List.of(), null, null, null, null, deadline);
    }

    public static LlmContext lab(String source, LabConfig config) {
        return new LlmContext(
                source,
                UUID.randomUUID(),
                null,
                null,
                List.of(),
                config.provider(),
                config.model(),
                config.temperature(),
                config.maxTokens(),
                null);
    }

    public LlmContext withPrompts(List<Long> ids) {
        return new LlmContext(
                source, traceId, userId, sessionId, ids, provider, model, temperature, maxTokens, deadline);
    }
}
