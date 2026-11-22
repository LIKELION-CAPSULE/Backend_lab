package com.capsule.session.dto;

import java.time.Instant;

/** api.md 17 연결 이어하기. 시간 칸은 마지막 heartbeat 값 */
public record ResumeResponse(
        long connectionId,
        Figure mate,
        Figure avatar,
        Me me,
        int connectedSeconds,
        int presentSeconds,
        int cameraOnSeconds,
        int pipSeconds,
        Integer minorRemainingSeconds,
        Instant serverTime) {}
