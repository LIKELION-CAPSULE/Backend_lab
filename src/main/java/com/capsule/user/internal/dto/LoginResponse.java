package com.capsule.user.internal.dto;

public record LoginResponse(String accessToken, long userId, boolean onboardingRequired) {}
