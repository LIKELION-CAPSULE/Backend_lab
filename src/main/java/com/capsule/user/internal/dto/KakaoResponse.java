package com.capsule.user.internal.dto;

public record KakaoResponse(String accessToken, long userId, boolean isNewUser, boolean onboardingRequired) {}
