package com.capsule.session.dto;

import java.time.Instant;

public record StartResponse(
        long sessionId,
        Instant startedAt,
        Integer targetMinutes,
        RoomView room,
        Figure character,
        Figure avatar,
        Me me,
        Instant serverTime) {}
