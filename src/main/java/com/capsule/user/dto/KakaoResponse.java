package com.capsule.user.dto;

public record KakaoResponse(String accessToken, long userId, boolean isNewUser, boolean onboardingRequired) {}
