package com.capsule.user.internal;

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
class User {

    private static final int NICKNAME_MAX = 20;

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

    static User emailUser(String email, String passwordHash, String nickname, Instant now) {
        return new User(nickname, email, passwordHash, now);
    }

    static User socialUser(String nickname, Instant now) {
        return new User(nickname, null, null, now);
    }

    /** 앞뒤 공백을 자르고 1~20자 (가입·프로필 설정 공통 규칙) */
    static String validNickname(String raw) {
        String nickname = raw == null ? "" : raw.strip();
        int length = nickname.codePointCount(0, nickname.length());
        if (length < 1 || length > NICKNAME_MAX) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NICKNAME");
        }
        return nickname;
    }

    /** 카카오 닉네임은 거절하지 않고 20자로 자른다 */
    static String fitNickname(String raw) {
        String nickname = raw == null ? "" : raw.strip();
        int length = nickname.codePointCount(0, nickname.length());
        return length <= NICKNAME_MAX ? nickname : nickname.substring(0, nickname.offsetByCodePoints(0, NICKNAME_MAX));
    }

    void loggedIn(Instant now) {
        this.lastLoginAt = now;
    }

    void updateProfile(String nickname, long avatarId, Instant now) {
        this.nickname = nickname;
        this.avatarId = avatarId;
        this.updatedAt = now;
    }

    long id() {
        return id;
    }

    String nickname() {
        return nickname;
    }

    String email() {
        return email;
    }

    String passwordHash() {
        return password;
    }

    Long avatarId() {
        return avatarId;
    }

    boolean isActive() {
        return withdrawnAt == null;
    }

    /** 닉네임은 가입 때 받으므로 아바타만 본다 (A1) */
    boolean onboardingRequired() {
        return avatarId == null;
    }
}
