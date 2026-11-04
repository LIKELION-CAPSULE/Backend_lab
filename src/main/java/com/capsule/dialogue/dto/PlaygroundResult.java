package com.capsule.dialogue.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 실험실: 한 번 실행의 n번째 결과. output은 kind마다 다르다 (chat: reply·emotion·affinity·trace 등) */
public record PlaygroundResult(
        int n,
        Map<String, Object> output,
        String error,
        long latencyMs,
        boolean overBudget,
        BigDecimal costUsd,
        List<UsageView> usages) {}
