package com.capsule.user.dto;

import java.util.List;

/** signupToken = 6번 403 KAKAO_SIGNUP_REQUIRED의 detail.signupToken (api.md 49) */
public record KakaoSignupRequest(String signupToken, List<Long> agreedTermVersionIds, String deviceHash) {}
