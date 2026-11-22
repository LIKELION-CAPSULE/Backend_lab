package com.capsule.dialogue.service;

import java.util.Optional;

/**
 * 사용자 말을 LLM에 넘기기 전에 보는 안전 판정 (api.md 20 '안전', PRD 11.1 · 피그마 SCR-36).
 * 위반이면 종류(safety_strikes.category)를 돌려준다. 빈 값이면 통과
 */
public interface SafetyChecker {

    Optional<String> violation(String userMessage);
}
