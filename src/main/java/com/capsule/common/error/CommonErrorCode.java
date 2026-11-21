package com.capsule.common.error;

import org.springframework.http.HttpStatus;

/** 모든 모듈이 같이 쓰는 에러 코드 (api.md '공통 규칙' 표 + 여러 API에 걸친 것) */
public enum CommonErrorCode implements ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    /** 토큰이 있는데 만료됐거나 잘못됨 → 프론트가 '토큰 갱신' 후 다시 요청 */
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS),
    /** 운영 설정 값이 틀림 (ADR 0026) */
    INVALID_SETTING(HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    CommonErrorCode(HttpStatus status) {
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
