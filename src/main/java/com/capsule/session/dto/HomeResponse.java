package com.capsule.session.dto;

import com.capsule.user.api.UserTypes.ActiveSessionSummary;
import java.util.List;

/**
 * api.md 11. today* = 오늘(오전 5시 기준) 끝낸 연결의 함께한 시간 · 연결 시간 합 (진행 중 연결은 빠짐).
 * lastMate = 마지막으로 연결한 메이트, 없으면 null (첫 방문)
 */
public record HomeResponse(
        int todayPresentSeconds,
        int todayConnectedSeconds,
        ActiveSessionSummary activeConnection,
        LastMate lastMate,
        List<MateCard> mates) {

    public record LastMate(long id, String name, String illustrationUrl) {}

    public record MateCard(
            long id,
            String name,
            String oneLiner,
            String ageRating,
            List<String> hashtags,
            String cardImageUrl,
            boolean comingSoon) {}
}
