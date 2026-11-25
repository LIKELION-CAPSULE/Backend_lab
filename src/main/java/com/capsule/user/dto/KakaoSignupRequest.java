package com.capsule.user.dto;

import java.util.List;

/** api.md 49 카카오 가입 완료 (2026-11-26): 6이 준 가입 토큰 + 약관 동의(4와 같은 규칙) + 기기 값 */
public record KakaoSignupRequest(String signupToken, List<Long> agreedTermVersionIds, String deviceHash) {}
