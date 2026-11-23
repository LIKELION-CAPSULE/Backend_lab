package com.capsule.content.domain;

import com.capsule.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** content 모듈 에러 코드 (api.md 31, 관리용 API는 ADR 0033) */
public enum ContentErrorCode implements ErrorCode {
    MATE_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** 관리용 API: 등록하려는 모델 · 메이트 값이 규칙에 안 맞음 (detail.reason) */
    INVALID_CONTENT(HttpStatus.BAD_REQUEST),
    MODEL_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** 다른 메이트나 아바타가 이미 쓰는 모델 (characters.model_id UNIQUE) */
    MODEL_IN_USE(HttpStatus.CONFLICT);

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
