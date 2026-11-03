package com.capsule.user.internal.dto;

public record Login(long userId, boolean onboardingRequired, boolean newUser) {}
