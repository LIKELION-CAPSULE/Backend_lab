package com.capsule.session.dto;

import java.time.Instant;
import java.util.List;

/** api.md 17 연결 이어하기. 시간 칸은 마지막 heartbeat 값. background = 끊기기 전 마지막 배경 (ADR 0032) */
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
        Instant serverTime,
        List<BackgroundView> backgrounds,
        String background) {}
