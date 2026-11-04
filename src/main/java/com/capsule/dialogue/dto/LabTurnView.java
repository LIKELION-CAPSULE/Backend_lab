package com.capsule.dialogue.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * 실험실: 대화 한 턴.
 *
 * @param overBudget 서비스였다면 5초를 넘겨 대체 대사가 나갔을 턴
 * @param usages 이 턴의 LLM 호출들 (감독·배우·심사 등)
 */
public record LabTurnView(
        long id,
        UUID traceId,
        String userText,
        String reply,
        String emotion,
        Integer affinity,
        String error,
        int latencyMs,
        boolean overBudget,
        BigDecimal costUsd,
        JsonNode trace,
        Integer rating,
        String ratingNote,
        Instant createdAt,
        List<UsageView> usages) {}
