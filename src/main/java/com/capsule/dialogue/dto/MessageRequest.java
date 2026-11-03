package com.capsule.dialogue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record MessageRequest(
        @NotNull UUID requestId,

        /** 대화할 캐릭터. 그 세션 캐릭터만 가능 (명세 20) */
        @NotNull Long characterId,

        @NotBlank(message = "EMPTY_MESSAGE") @Size(max = 300)
        String content,

        @NotNull InputType inputType,
        Boolean tts) {}
