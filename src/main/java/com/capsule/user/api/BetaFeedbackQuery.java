package com.capsule.user.api;

/** 베타 피드백 조회 (api.md 23 betaFeedbackAvailable). session이 연결 기록에 붙인다 */
public interface BetaFeedbackQuery {

    /** 오늘(오전 5시 기준) 베타 평가(RATING)를 아직 안 했으면 true */
    boolean ratingAvailableToday(long userId);
}
