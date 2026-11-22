package com.capsule.session.dto;

import java.time.Instant;

/** api.md 16 연결 시작 */
public record StartResponse(
        long connectionId,
        Instant startedAt,
        Figure mate,
        Figure avatar,
        Me me,
        Instant serverTime,
        boolean firstMeet,
        ReactionView greeting,
        Integer minorRemainingSeconds) {}
