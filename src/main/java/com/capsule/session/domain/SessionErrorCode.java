package com.capsule.session.domain;

import com.capsule.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** session 모듈 에러 코드 (api.md 11, 16~18, 21~23). 2026-11-23 SESSION_* → CONNECTION_*, 룸 · 목표 시간 코드 삭제 */
public enum SessionErrorCode implements ErrorCode {
    MATE_NOT_FOUND(HttpStatus.NOT_FOUND),
    MATE_COMING_SOON(HttpStatus.CONFLICT),
    CONNECTION_NOT_FOUND(HttpStatus.NOT_FOUND),
    ONBOARDING_REQUIRED(HttpStatus.FORBIDDEN),
    MINOR_DAILY_LIMIT(HttpStatus.FORBIDDEN),
    INVALID_EVENT(HttpStatus.BAD_REQUEST),
    INVALID_CONNECTION_TIME(HttpStatus.BAD_REQUEST),
    /** detail.connectionId = 진행 중 연결 */
    CONNECTION_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT),
    CONNECTION_IN_PROGRESS(HttpStatus.CONFLICT),
    CONNECTION_ENDED(HttpStatus.CONFLICT);

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
