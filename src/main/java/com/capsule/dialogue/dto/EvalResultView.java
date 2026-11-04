package com.capsule.dialogue.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EvalResultView(
        long id,
        long caseId,
        String caseName,
        int repeatNo,
        UUID traceId,
        List<TranscriptTurn> transcript,
        BigDecimal judgeScore,
        String judgeNote,
        Integer humanScore,
        String humanNote,
        int latencyMs,
        BigDecimal costUsd,
        String error,
        Instant createdAt) {}
