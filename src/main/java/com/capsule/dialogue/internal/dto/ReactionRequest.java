package com.capsule.dialogue.internal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** intensity는 AWAY일 때만 쓰고 다른 이벤트는 무시 (범위 밖이면 이벤트와 상관없이 400) */
public record ReactionRequest(
        @NotNull UUID requestId, @Min(1) @Max(3) Integer intensity, Boolean tts) {}
