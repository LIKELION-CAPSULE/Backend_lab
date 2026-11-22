package com.capsule.user.dto;

import com.capsule.credit.api.CreditTypes.Credits;
import com.capsule.user.api.UserTypes.ActiveSessionSummary;
import java.time.LocalDate;

/**
 * profileImage = 아바타 썸네일 (B7). isMinor = 만 19세 미만. activeConnection = 진행 중 연결 (api.md 8).
 * credits = GNB 크레딧 (7단계 · 2026-11-23)
 */
public record MeResponse(
        long userId,
        String nickname,
        String email,
        boolean onboardingRequired,
        ActiveSessionSummary activeConnection,
        String profileImage,
        LocalDate birthDate,
        boolean isMinor,
        String honorific,
        String aboutMe,
        String referralCode,
        Credits credits) {}
