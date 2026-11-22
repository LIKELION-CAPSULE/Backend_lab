package com.capsule.user.domain;

import com.capsule.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** user 모듈 에러 코드 (api.md 1~8, 10, 13, 14, 24~27, 29, 30) */
public enum UserErrorCode implements ErrorCode {
    // 가입 · 인증
    INVALID_EMAIL(HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST),
    INVALID_NICKNAME(HttpStatus.BAD_REQUEST),
    TERMS_REQUIRED(HttpStatus.BAD_REQUEST),
    EMAIL_ALREADY_USED(HttpStatus.CONFLICT),
    CODE_EXPIRED(HttpStatus.BAD_REQUEST),
    INVALID_CODE(HttpStatus.BAD_REQUEST),
    TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS),
    VERIFICATION_EXPIRED(HttpStatus.BAD_REQUEST),
    UNDER_14(HttpStatus.BAD_REQUEST),
    INVALID_BIRTH_DATE(HttpStatus.BAD_REQUEST),
    BETA_FULL(HttpStatus.FORBIDDEN),
    // 로그인 · 토큰
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS),
    SOCIAL_AUTH_FAILED(HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST),
    // 프로필 · 일정 · 탈퇴
    AVATAR_NOT_FOUND(HttpStatus.BAD_REQUEST),
    INVALID_HONORIFIC(HttpStatus.BAD_REQUEST),
    INVALID_ABOUT_ME(HttpStatus.BAD_REQUEST),
    INVALID_SETTINGS(HttpStatus.BAD_REQUEST),
    INVALID_MEMORY(HttpStatus.BAD_REQUEST),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST),
    CONNECTION_IN_PROGRESS(HttpStatus.CONFLICT); // 2026-11-23 SESSION_IN_PROGRESS에서 이름 변경

    private final HttpStatus status;

    UserErrorCode(HttpStatus status) {
        this.status = status;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return name();
    }
}
