package com.capsule.relation.domain;

import com.capsule.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** relation 모듈 에러 코드 (실험실) */
public enum RelationErrorCode implements ErrorCode {
    SESSION_REQUIRED(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    RelationErrorCode(HttpStatus status) {
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
