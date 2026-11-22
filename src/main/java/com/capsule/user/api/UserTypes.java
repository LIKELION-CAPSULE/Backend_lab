package com.capsule.user.api;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** user 모듈이 내보내는 DTO 모음. */
public final class UserTypes {

    private UserTypes() {}

    /**
     * profileImageUrl = 고른 아바타의 썸네일 (B7). 아바타 전이면 null. onboardingRequired = 아바타 또는 생년월일이 없음 (api.md 8).
     * honorific = 메이트가 부르는 이름(없으면 null → 닉네임), aboutMe = 나에 대해 한 줄, minor = 만 19세 미만 (12/1 베타)
     */
    public record UserProfile(
            long id,
            String nickname,
            String email,
            Long avatarId,
            String profileImageUrl,
            boolean onboardingRequired,
            String honorific,
            String aboutMe,
            boolean minor) {

        /** 계약 확장 전 모양 (호칭 · 나에 대해 없음, 성인). 테스트 목이 쓰던 생성자를 깨지 않으려고 남긴다 */
        public UserProfile(
                long id,
                String nickname,
                String email,
                Long avatarId,
                String profileImageUrl,
                boolean onboardingRequired) {
            this(id, nickname, email, avatarId, profileImageUrl, onboardingRequired, null, null, false);
        }
    }

    /** type: EXAM · GOAL · PROMISE. dueDate·dueTime은 한국 날짜·시간 그대로 */
    public record MemoryInfo(long id, String type, String title, LocalDate dueDate, LocalTime dueTime) {}

    /** 진행 중 세션 요약 (내 정보 조회의 activeSession, H18 팝업) */
    public record ActiveSessionSummary(
            long sessionId, long roomId, String roomName, String characterName, Instant startedAt) {}
}
