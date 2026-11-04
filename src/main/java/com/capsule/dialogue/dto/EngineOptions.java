package com.capsule.dialogue.dto;

/**
 * 대화 엔진(ENGINE 파이프라인) 실험 옵션. null이면 엔진 기본값 (빠른 모드, 감독 켬, 사건 빈도 0.3).
 *
 * @param quality true면 배우 후보 3개 + 심사 (느리고 비쌈)
 * @param useDirector false면 감독 LLM을 건너뛰고 코드 규칙 계획만 쓴다 (5초 제한이 빠듯할 때)
 */
public record EngineOptions(Boolean quality, Boolean useDirector, Double eventFreq) {
    public static final EngineOptions DEFAULT = new EngineOptions(null, null, null);
}
