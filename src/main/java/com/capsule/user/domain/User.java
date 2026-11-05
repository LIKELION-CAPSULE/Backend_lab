package com.capsule.user.domain;

import com.capsule.common.error.ApiException;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/** users 테이블. created_at은 DB 기본값, profile_image_url은 쓰지 않아서(B7) 매핑하지 않는다. */
@Entity
@Table(name = "users")
public class User {

    private static final int NICKNAME_MAX = 20;
    public static final String WITHDRAWN_NICKNAME = "탈퇴한 사용자";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nickname;
    private String email;
    private String password;
    private Long avatarId;
    private Instant lastLoginAt;
    private Instant updatedAt;
    private Instant withdrawnAt;

    protected User() {}

    private User(String nickname, String email, String passwordHash, Instant now) {
        this.nickname = nickname;
        this.email = email;
        this.password = passwordHash;
        this.lastLoginAt = now;
        this.updatedAt = now;
    }

    public static User emailUser(String email, String passwordHash, String nickname, Instant now) {
        return new User(nickname, email, passwordHash, now);
    }

    public static User socialUser(String nickname, Instant now) {
        return new User(nickname, null, null, now);
    }

    /** 앞뒤 공백을 자르고 1~20자 (가입·프로필 설정 공통 규칙) */
    public static String validNickname(String raw) {
        String nickname = raw == null ? "" : raw.strip();
        int length = nickname.codePointCount(0, nickname.length());
        if (length < 1 || length > NICKNAME_MAX) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NICKNAME");
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
        this.updatedAt = now;
        this.withdrawnAt = now;
    }

    public void updateProfile(String nickname, long avatarId, Instant now) {
        this.nickname = nickname;
        this.avatarId = avatarId;
        this.updatedAt = now;
    }

    public long id() {
        return id;
    }

    public String nickname() {
        return nickname;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return password;
    }

    public Long avatarId() {
        return avatarId;
    }

    public boolean isActive() {
        return withdrawnAt == null;
    }

    /** 닉네임은 가입 때 받으므로 아바타만 본다 (A1) */
    public boolean onboardingRequired() {
        return avatarId == null;
    }
}
