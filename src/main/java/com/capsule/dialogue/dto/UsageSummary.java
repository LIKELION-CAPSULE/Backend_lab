package com.capsule.dialogue.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 실험실: 하루 × 출처 × 공급자 × 모델 × 용도별 LLM 호출 집계. over5s = llm-budget(5초, 명세 대체 대사 기준)을 넘긴 호출 수 */
public record UsageSummary(
        LocalDate day,
        String source,
        String provider,
        String model,
        String purpose,
        long calls,
        long errors,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens,
        BigDecimal costUsd,
        double avgLatencyMs,
        double p95LatencyMs,
        long over5s) {}
