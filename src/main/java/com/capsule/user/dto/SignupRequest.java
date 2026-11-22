package com.capsule.user.dto;

import java.util.List;

/** birthDate = YYYY-MM-DD, deviceHash = 프론트가 만든 기기 값 (api.md 4) */
public record SignupRequest(
        String verificationToken,
        String password,
        String nickname,
        List<Long> agreedTermVersionIds,
        String birthDate,
        String deviceHash) {}
