package com.capsule.dialogue.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** intensity는 AWAY일 때만 쓰고(1~3, 범위 밖이면 400) 다른 이벤트는 무시. 이벤트 종류를 알아야 해서 서비스가 검사 */
public record ReactionRequest(@NotNull UUID requestId, Integer intensity, Boolean tts) {}
