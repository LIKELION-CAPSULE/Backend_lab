package com.capsule.dialogue.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record MessageRequest(
        @NotNull UUID requestId,

        @NotBlank(message = "EMPTY_MESSAGE") @Size(max = 300)
        String content,

        @NotNull InputType inputType,
        Boolean tts) {

    /** inputType은 검증만 한다 (저장할 컬럼 없음) */
    enum InputType {
        TEXT,
        QUICK
    }
}
