package com.capsule.session.dto;

import java.time.Instant;

/** api.md 21. minorRemainingSeconds = 만 19세 미만이면 오늘 남은 연결 시간, 아니면 null */
public record HeartbeatResponse(Integer minorRemainingSeconds, Instant serverTime) {}
