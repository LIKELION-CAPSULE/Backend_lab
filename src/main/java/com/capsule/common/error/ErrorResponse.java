package com.capsule.common.error;

import java.util.Map;

/** 모든 에러 응답 형식 (skeleton-plan B13). 예: {"code":"SESSION_ALREADY_IN_PROGRESS","message":"...","detail":{"sessionId":512}} */
public record ErrorResponse(String code, String message, Map<String, Object> detail) {

    public static ErrorResponse of(String code) {
        return new ErrorResponse(code, code, Map.of());
    }
}
