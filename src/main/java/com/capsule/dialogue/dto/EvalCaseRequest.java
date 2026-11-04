package com.capsule.dialogue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** 실험실: 평가 케이스. turns = 사용자 말을 차례대로, context = 가상 사용자 정보(닉네임·호감도 단계·요약·일정) */
public record EvalCaseRequest(
        @NotBlank String name,
        @NotNull Long characterId,
        @NotEmpty List<String> turns,
        LabConfig context,
        String rubric) {}
