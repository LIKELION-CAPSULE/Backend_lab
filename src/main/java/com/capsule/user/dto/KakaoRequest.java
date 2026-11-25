package com.capsule.user.dto;

/**
 * 약관 동의는 받지 않는다 (api.md 6). 새 사용자는 가입 토큰을 받아 49에서 약관 동의와 함께 가입한다 (2026-11-26).
 * deviceHash는 모양만 검사하고, 저장은 49에서
 */
public record KakaoRequest(String code, String redirectUri, String deviceHash) {}
