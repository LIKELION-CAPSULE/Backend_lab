package com.capsule.session.domain;

import com.capsule.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** session 모듈 에러 코드 (api.md 11, 12, 16~18, 21~23) */
public enum SessionErrorCode implements ErrorCode {
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND),
    CHARACTER_NOT_IN_ROOM(HttpStatus.BAD_REQUEST),
    ONBOARDING_REQUIRED(HttpStatus.FORBIDDEN),
    INVALID_TARGET_MINUTES(HttpStatus.BAD_REQUEST),
    INVALID_EVENT(HttpStatus.BAD_REQUEST),
    INVALID_STUDY_TIME(HttpStatus.BAD_REQUEST),
    /** detail.sessionId = 진행 중 세션 */
    SESSION_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT),
    SESSION_IN_PROGRESS(HttpStatus.CONFLICT),
    SESSION_ENDED(HttpStatus.CONFLICT);

    private final HttpStatus status;

    SessionErrorCode(HttpStatus status) {
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
