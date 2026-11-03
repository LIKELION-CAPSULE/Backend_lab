package com.capsule.user.dto;

public record Login(long userId, boolean onboardingRequired, boolean newUser) {}
