package com.capsule.dialogue.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 실험실: 평가 실행과 집계. 목록에서는 results가 비어 있다.
 *
 * @param expected 케이스 수 × 반복 (done이 여기 닿으면 끝)
 * @param avgJudge LLM 심사 평균 (0~10), avgHuman 사람 평가 평균 (1~5). 값이 없으면 null
 */
public record EvalRunView(
        long id,
        String label,
        String pipeline,
        LabConfig config,
        List<Long> caseIds,
        int repeats,
        boolean judge,
        String status,
        Instant createdAt,
        Instant finishedAt,
        int expected,
        int done,
        int errors,
        Double avgJudge,
        Double avgHuman,
        Double avgLatencyMs,
        BigDecimal costUsd,
        List<EvalResultView> results) {}
