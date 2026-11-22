package com.capsule.user.dto;

import java.time.Instant;

/** api.md 35 friends[]. 탈퇴한 친구는 nickname null. status: JOINED(코드 입력함) · QUALIFIED(첫 연결 완료) */
public record ReferralFriend(String nickname, String status, boolean rewarded, Instant joinedAt) {

    public static final String JOINED = "JOINED";
    public static final String QUALIFIED = "QUALIFIED";
}
