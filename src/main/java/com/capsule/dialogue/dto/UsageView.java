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
                u.getId(),
                u.getPurpose(),
                u.getProvider(),
                u.getModel(),
                u.getStatus(),
                u.getError(),
                u.getStopReason(),
                u.getInputTokens(),
                u.getOutputTokens(),
                u.getCacheReadTokens(),
                u.getCacheWriteTokens(),
                u.getCostUsd(),
                u.getLatencyMs(),
                u.getTtftMs(),
                u.getPromptVersionIds(),
                u.getRequest() == null ? null : LlmJson.tree(u.getRequest()),
                u.getResponse(),
                u.getCreatedAt());
    }
}
