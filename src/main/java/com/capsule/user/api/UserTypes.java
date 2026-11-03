package com.capsule.user.api;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** user 모듈이 내보내는 DTO 모음. */
public final class UserTypes {

    private UserTypes() {}

    /** profileImageUrl = 고른 아바타의 썸네일 (B7). 아바타 전이면 null. onboardingRequired = 아바타를 아직 안 고름 (A1) */
    public record UserProfile(
            long id,
            String nickname,
            String email,
            Long avatarId,
            String profileImageUrl,
            boolean onboardingRequired) {}

    /** type: EXAM · GOAL · PROMISE. dueDate·dueTime은 한국 날짜·시간 그대로 */
    public record MemoryInfo(long id, String type, String title, LocalDate dueDate, LocalTime dueTime) {}

    /** 진행 중 세션 요약 (내 정보 조회의 activeSession, H18 팝업) */
    public record ActiveSessionSummary(
            long sessionId, long roomId, String roomName, String characterName, Instant startedAt) {}
}
