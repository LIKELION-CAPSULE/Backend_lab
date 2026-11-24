package com.capsule.session.dto;

import java.time.Instant;

/** api.md 22 · 23 연결 끊기 · 연결 기록 */
public record ReportResponse(
        long connectionId,
        Instant startedAt,
        Instant endedAt,
        String endType,
        int connectedSeconds,
        int presentSeconds,
        int awaySeconds,
        int cameraOnSeconds,
        int messageCount,
        int returnCount,
        MateRef mate,
        IntimacyView relation,
        String closingLine,
        ReferralRewardView referralReward,
        boolean betaFeedbackAvailable) {

    public record MateRef(long id, String name, String illustrationUrl, String myRole) {}
}
