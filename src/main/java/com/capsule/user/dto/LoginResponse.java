package com.capsule.user.dto;

public record LoginResponse(String accessToken, long userId, boolean onboardingRequired) {}
