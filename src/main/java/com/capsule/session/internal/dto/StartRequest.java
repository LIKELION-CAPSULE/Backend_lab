package com.capsule.session.internal.dto;

import jakarta.validation.constraints.NotNull;

public record StartRequest(@NotNull Long roomId, @NotNull Long characterId, Integer targetMinutes) {}
