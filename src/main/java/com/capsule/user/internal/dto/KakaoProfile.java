package com.capsule.user.internal.dto;

/** 카카오가 돌려준 회원번호·닉네임 */
public record KakaoProfile(String providerUserId, String nickname) {}
