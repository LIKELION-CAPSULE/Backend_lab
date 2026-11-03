package com.capsule.user.dto;

import java.util.List;

public record SignupRequest(
        String verificationToken, String password, String nickname, List<Long> agreedTermVersionIds) {}
