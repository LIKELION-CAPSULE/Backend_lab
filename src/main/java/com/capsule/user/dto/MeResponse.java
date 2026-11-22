package com.capsule.user.dto;

import com.capsule.user.api.UserTypes.ActiveSessionSummary;
import java.time.LocalDate;

/** profileImage = 아바타 썸네일 (B7). isMinor = 만 19세 미만. activeConnection = 진행 중 연결 (api.md 8) */
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
        String referralCode) {}
