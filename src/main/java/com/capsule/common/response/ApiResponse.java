package com.capsule.common.response;

/**
 * 본문이 있는 모든 성공 응답 형식 (api.md '공통 규칙', ADR 0030). 예: {"success":true,"data":{"terms":[...]}}
 * 컨트롤러가 {@code ApiResponse.ok(dto)}를 직접 반환한다. 204 · 202 '본문 없음'과 SSE 이벤트는 감싸지 않는다
 */
public record ApiResponse<T>(boolean success, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data);
    }
}
