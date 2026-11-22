package com.capsule.session.dto;

import java.time.Instant;

/** api.md 17 연결 이어하기 */
public record ResumeResponse(
        long connectionId, Figure mate, Figure avatar, Me me, int studySeconds, int breakSeconds, Instant serverTime) {}
