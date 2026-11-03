package com.capsule.common.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** 명세의 에러 코드(INVALID_EMAIL 등)를 그대로 내보내는 예외. 응답 형식은 {@link ErrorResponse}. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> detail;

    public ApiException(HttpStatus status, String code) {
        this(status, code, Map.of());
    }

    public ApiException(HttpStatus status, String code, Map<String, Object> detail) {
        super(code);
        this.status = status;
        this.code = code;
        this.detail = detail;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> detail() {
        return detail;
    }
}
