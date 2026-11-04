package com.capsule.dialogue.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 실험실: 사람 평가 1~5 (null = 지움)와 메모 */
public record RatingRequest(@Min(1) @Max(5) Integer score, String note) {}
