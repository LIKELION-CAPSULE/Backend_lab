package com.capsule.dialogue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record MessageRequest(
        @NotNull UUID requestId,

        @NotBlank(message = "EMPTY_MESSAGE") @Size(max = 300)
        String content,

        @NotNull InputType inputType,
        Boolean tts) {}
