package com.capsule.session.dto;

import java.time.Instant;
import java.util.List;

/**
 * api.md 21. minorRemainingSeconds = 만 19세 미만이면 오늘 남은 연결 시간, 아니면 null.
 * stageUp = 이번 heartbeat로 처음 닿은 단계 (연결 중 '새 표정이 열렸어요', SCR-56, 2026-11-24), 없으면 null
 */
public record HeartbeatResponse(Integer minorRemainingSeconds, Instant serverTime, StageUp stageUp) {

    public record StageUp(int stage, String stageName, List<IntimacyView.Unlocked> unlocked) {}
}
