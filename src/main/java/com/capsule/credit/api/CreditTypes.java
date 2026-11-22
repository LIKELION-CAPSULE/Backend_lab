package com.capsule.credit.api;

import java.time.Instant;

/** credit 모듈이 내보내는 DTO (api.md 33 Credits, ADR 0031) */
public final class CreditTypes {

    private CreditTypes() {}

    /**
     * 크레딧 요약 (8 · 16 · 33이 같은 모양).
     *
     * @param todayRemaining 지금 쓸 수 있는 양 = 출석분 + 초대분
     * @param attendanceExpiresAt 출석분이 사라지는 시각 (다음 오전 5시)
     * @param low 100 미만
     * @param exhausted 0
     */
    public record Credits(
            int todayRemaining,
            int attendanceRemaining,
            Instant attendanceExpiresAt,
            int inviteBalance,
            boolean low,
            boolean exhausted) {}

    /**
     * 차감 결과.
     *
     * @param spent 차감했으면 true (같은 요청을 다시 보내도 true). 모자라면 false이고 아무것도 바뀌지 않음
     */
    public record SpendResult(boolean spent, Credits credits) {}
}
