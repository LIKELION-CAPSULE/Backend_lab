package com.capsule.user.dto;

/** 약관 동의는 요청으로 받지 않는다 (api.md 6, 가입하면 자동 동의). 예전처럼 agreedTermVersionIds를 보내도 무시된다 */
public record KakaoRequest(String code, String redirectUri) {}
