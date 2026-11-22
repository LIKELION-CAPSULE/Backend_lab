package com.capsule.session.dto;

import com.capsule.user.api.UserTypes.ActiveSessionSummary;
import java.util.List;

/**
 * api.md 11. todayStudySeconds = 오늘(한국 날짜) 끝낸 연결의 공부 시간 합 (진행 중 연결은 빠짐).
 * lastMate = 마지막으로 연결한 메이트, 없으면 null (첫 방문)
 */
public record HomeResponse(
        int todayStudySeconds, ActiveSessionSummary activeConnection, LastMate lastMate, List<MateCard> mates) {

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
