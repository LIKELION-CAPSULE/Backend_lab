package com.capsule.user.domain;

import com.capsule.common.error.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** users 테이블. created_at은 DB 기본값, profile_image_url은 쓰지 않아서(B7) 매핑하지 않는다. */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    private static final int NICKNAME_MAX = 20;
    private static final int HONORIFIC_MAX = 10;
    private static final int ABOUT_ME_MAX = 50; // 2026-11-23 60 → 50 (피그마 SCR-27 P-3 초안)
    public static final String WITHDRAWN_NICKNAME = "탈퇴한 사용자";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nickname;
    private String email;

    @Getter(AccessLevel.NONE) // 해시는 passwordHash()로만 꺼낸다
    private String password;

    private Long avatarId;
    private Instant lastLoginAt;
    private Instant updatedAt;
    private Instant withdrawnAt;
    private LocalDate birthDate;
    private String honorific;
    private String aboutMe;
    private String referralCode;

    /** DB 기본값. 저장한 그 트랜잭션 안에서는 null (35 · 36 초대 코드 입력 마감에만 씀) */
    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    // settings(jsonb)는 엔티티에 매핑하지 않는다: SettingsService가 SQL로 읽고 쓴다 (DB 기본값 '{}')

    private User(String nickname, String email, String passwordHash, String referralCode, Instant now) {
        this.nickname = nickname;
        this.email = email;
        this.password = passwordHash;
        this.referralCode = referralCode;
        this.lastLoginAt = now;
        this.updatedAt = now;
    }

    /** 이메일 가입은 생년월일을 가입 때 받는다 (api.md 4) */
    public static User emailUser(
            String email, String passwordHash, String nickname, LocalDate birthDate, String referralCode, Instant now) {
        User user = new User(nickname, email, passwordHash, referralCode, now);
        user.birthDate = birthDate;
        return user;
    }

    /** 카카오 가입은 생년월일을 온보딩(api.md 10)에서 받는다 */
    public static User socialUser(String nickname, String referralCode, Instant now) {
        return new User(nickname, null, null, referralCode, now);
    }

    /** 앞뒤 공백을 자르고 1~10자. 빈 문자열이면 null(지움) (api.md 10) */
    public static String validHonorific(String raw) {
        return optionalText(raw, HONORIFIC_MAX, UserErrorCode.INVALID_HONORIFIC);
    }

    /** 앞뒤 공백을 자르고 0~60자. 빈 문자열이면 null(지움) (api.md 10) */
    public static String validAboutMe(String raw) {
        return optionalText(raw, ABOUT_ME_MAX, UserErrorCode.INVALID_ABOUT_ME);
    }

    private static String optionalText(String raw, int max, UserErrorCode error) {
        String text = raw.strip();
        if (text.isEmpty()) {
            return null;
        }
        if (text.codePointCount(0, text.length()) > max) {
            throw new ApiException(error);
        }
        return text;
    }

    /** 앞뒤 공백을 자르고 1~20자 (가입·프로필 설정 공통 규칙) */
    public static String validNickname(String raw) {
        String nickname = raw == null ? "" : raw.strip();
        int length = nickname.codePointCount(0, nickname.length());
        if (length < 1 || length > NICKNAME_MAX) {
            throw new ApiException(UserErrorCode.INVALID_NICKNAME);
        }
        return nickname;
    }

    /** 카카오 닉네임은 거절하지 않고 20자로 자른다 */
    public static String fitNickname(String raw) {
        String nickname = raw == null ? "" : raw.strip();
        int length = nickname.codePointCount(0, nickname.length());
        return length <= NICKNAME_MAX ? nickname : nickname.substring(0, nickname.offsetByCodePoints(0, NICKNAME_MAX));
    }

    public void loggedIn(Instant now) {
        this.lastLoginAt = now;
    }

    /** 비밀번호 재설정 (api.md 26). passwordHash = bcrypt 해시 */
    public void changePassword(String passwordHash, Instant now) {
        this.password = passwordHash;
        this.updatedAt = now;
    }

    /** 회원 탈퇴 (api.md 27): 행은 남기고 개인정보만 비운다. 닉네임은 NOT NULL이라 고정 문구 */
    public void withdraw(Instant now) {
        this.nickname = WITHDRAWN_NICKNAME;
        this.email = null;
        this.password = null;
        this.avatarId = null;
        this.birthDate = null;
        this.honorific = null;
        this.aboutMe = null;
        this.updatedAt = now;
        this.withdrawnAt = now;
    }

    public void updateProfile(String nickname, long avatarId, Instant now) {
        this.nickname = nickname;
        this.avatarId = avatarId;
        this.updatedAt = now;
    }

    /** 호칭 · 나에 대해 (null = 지움). 검사는 validHonorific · validAboutMe */
    public void updateHonorific(String honorific, Instant now) {
        this.honorific = honorific;
        this.updatedAt = now;
    }

    public void updateAboutMe(String aboutMe, Instant now) {
        this.aboutMe = aboutMe;
        this.updatedAt = now;
    }

    /** 생년월일은 비어 있을 때 한 번만 (api.md 10). 이미 있으면 그대로 둔다 */
    public void fillBirthDate(LocalDate birthDate, Instant now) {
        if (this.birthDate == null) {
            this.birthDate = birthDate;
            this.updatedAt = now;
        }
    }

    public String passwordHash() {
        return password;
    }

    public boolean isActive() {
        return withdrawnAt == null;
    }

    /** 닉네임은 가입 때 받으므로 아바타와 생년월일만 본다 (api.md 8. 카카오 · 베타 전 가입자는 온보딩에서 생년월일) */
    public boolean onboardingRequired() {
        return avatarId == null || birthDate == null;
    }

    /** 만 19세 미만 (한국 날짜 기준). 생년월일이 없으면 false (api.md 8) */
    public boolean isMinor(LocalDate today) {
        return birthDate != null && Age.of(birthDate, today) < Age.ADULT;
    }
}
