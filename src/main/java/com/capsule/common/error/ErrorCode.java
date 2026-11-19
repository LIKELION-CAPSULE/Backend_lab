package com.capsule.common.error;

import org.springframework.http.HttpStatus;

/**
 * 명세의 에러 코드 하나 (HTTP 상태 + 응답 {@code code}). 모듈마다 {@code <모듈>/domain/<모듈>ErrorCode} enum으로 구현하고,
 * 모든 모듈이 같이 쓰는 코드는 {@link CommonErrorCode}. 던질 때는 {@code throw new ApiException(UserErrorCode.INVALID_EMAIL)} (ADR 0030)
 */
public interface ErrorCode {

    HttpStatus status();

    /** 응답의 {@code code}. 보통 enum 이름과 같다 */
    String code();
}
