package com.capsule.dialogue.dto;

import jakarta.validation.constraints.NotBlank;

/** 실험실: 새 프롬프트 버전. characterId null = 공통, activate면 바로 켠다 */
public record PromptRequest(
        @NotBlank String key, Long characterId, @NotBlank String body, String note, boolean activate) {}
