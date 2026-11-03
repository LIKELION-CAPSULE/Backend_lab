package com.capsule.session.dto;

import java.time.Instant;

public record ResumeResponse(
        long sessionId,
        Integer targetMinutes,
        RoomView room,
        Figure character,
        Figure avatar,
        Me me,
        int studySeconds,
        int breakSeconds,
        Instant serverTime) {}
