package com.capsule.user.dto;

/**
 * 약관 동의는 받지 않는다: 새 사용자는 가입 토큰을 받아 49번({@link KakaoSignupRequest})에서 동의한다 (api.md 6).
 * 예전처럼 agreedTermVersionIds를 보내도 무시된다. deviceHash는 형식만 보고 저장하지 않는다
 */
public record KakaoRequest(String code, String redirectUri, String deviceHash) {}
