package com.capsule.session.dto;

import jakarta.validation.constraints.NotNull;

/** api.md 16 연결 시작. 2026-11-23 룸 · 캐릭터 · 목표 시간 → 메이트 하나 */
public record StartRequest(@NotNull Long mateId) {}
