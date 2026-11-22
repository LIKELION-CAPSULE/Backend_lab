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

    /**
     * 진행 중 연결 요약 (내 정보 · 홈의 activeConnection, 이어하기 팝업). 2026-11-23 룸 칸을 빼고 메이트로 (api.md 8 · 11).
     * user · session만 쓰는 계약이라 제자리에서 바꿈 (dialogue · relation 사용처 없음)
     */
    public record ActiveSessionSummary(long connectionId, long mateId, String mateName, Instant startedAt) {}

    /** 연결로 받은 초대 보상 (받은 사람 몫, api.md 22 · 23 referralReward) */
    public record ReferralReward(int amount) {}

    /** 설정 7개 (api.md 29와 같은 모양 · 기본값 포함). 16 연결 시작의 settings (2026-11-23) */
    public record SettingsInfo(
            String talkFrequency,
            boolean voiceEnabled,
            int voiceVolume,
            String voiceSpeed,
            boolean pipBubble,
            String defaultMode,
            String pipSize) {}
}
