package com.capsule.dialogue.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 실험실: 평가 실행. config의 공급자·모델·프롬프트 고정이 실험 조건이다 (가상 사용자 정보는 케이스의 context).
 *
 * @param judge true면 결과마다 LLM 심사관이 0~10점 (심사관은 실험 조건과 상관없이 기본 공급자의 judge 등급)
 */
public record EvalRunRequest(
        @NotBlank String label,
        @NotEmpty List<Long> caseIds,
        @Min(1) @Max(20) int repeats,
        String pipeline,
        LabConfig config,
        boolean judge) {}
