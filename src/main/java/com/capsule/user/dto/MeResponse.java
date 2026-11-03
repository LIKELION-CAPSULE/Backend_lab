package com.capsule.user.dto;

import com.capsule.user.api.UserTypes.ActiveSessionSummary;

/** profileImage = 아바타 썸네일 (B7) */
public record MeResponse(
        long userId,
        String nickname,
        String email,
        boolean onboardingRequired,
        ActiveSessionSummary activeSession,
        String profileImage) {}
