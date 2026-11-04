package com.capsule.dialogue.dto;

import com.capsule.dialogue.client.LlmJson;
import com.capsule.dialogue.domain.LlmUsage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** 실험실: LLM 호출 한 번 (llm_usages 한 행). request·response는 원문을 저장한 호출만 */
public record UsageView(
        long id,
        String purpose,
        String provider,
        String model,
        String status,
        String error,
        String stopReason,
        int inputTokens,
        int outputTokens,
        int cacheReadTokens,
        int cacheWriteTokens,
        BigDecimal costUsd,
        int latencyMs,
        Integer ttftMs,
        List<Long> promptVersionIds,
        JsonNode request,
        String response,
        Instant createdAt) {

    public static UsageView from(LlmUsage u) {
        return new UsageView(
                u.id(),
                u.purpose(),
                u.provider(),
                u.model(),
                u.status(),
                u.error(),
                u.stopReason(),
                u.inputTokens(),
                u.outputTokens(),
                u.cacheReadTokens(),
                u.cacheWriteTokens(),
                u.costUsd(),
                u.latencyMs(),
                u.ttftMs(),
                u.promptVersionIds(),
                u.request() == null ? null : LlmJson.tree(u.request()),
                u.response(),
                u.createdAt());
    }
}
