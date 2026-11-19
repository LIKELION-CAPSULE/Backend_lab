package com.capsule.common.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** 명세의 에러 코드({@link ErrorCode})를 그대로 내보내는 예외. 응답 형식은 {@link ErrorResponse}. */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> detail;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, Map.of());
    }

    public ApiException(ErrorCode errorCode, Map<String, Object> detail) {
        super(errorCode.code());
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public HttpStatus status() {
        return errorCode.status();
    }

    public String code() {
        return errorCode.code();
    }

    public Map<String, Object> detail() {
        return detail;
    }
}
