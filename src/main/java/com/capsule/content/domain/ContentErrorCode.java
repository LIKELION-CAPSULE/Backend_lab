package com.capsule.content.domain;

import com.capsule.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** content 모듈 에러 코드 (api.md 31) */
public enum ContentErrorCode implements ErrorCode {
    MATE_NOT_FOUND(HttpStatus.NOT_FOUND);

    private final HttpStatus status;

    ContentErrorCode(HttpStatus status) {
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
