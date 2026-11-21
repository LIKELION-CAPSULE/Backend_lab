package com.capsule.common.error;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * 모든 에러 응답 형식 (api.md '공통 규칙', ADR 0030). 예:
 * {"success":false,"code":"SESSION_ALREADY_IN_PROGRESS","message":"...","detail":{"sessionId":512}}
 * {@code message}는 지금 {@code code}와 같은 값 (화면용 문구는 확정 필요)
 */
public record ErrorResponse(boolean success, String code, String message, Map<String, Object> detail) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    public static ErrorResponse of(String code) {
        return of(code, Map.of());
    }

    public static ErrorResponse of(String code, Map<String, Object> detail) {
        return new ErrorResponse(false, code, code, detail);
    }

    public static ErrorResponse of(ErrorCode code) {
        return of(code.code());
    }

    public static ErrorResponse of(ApiException e) {
        return of(e.code(), e.detail());
    }

    /** 컨트롤러 밖(보안 필터)에서 에러 응답을 쓸 때. 모양은 위 record 그대로 */
    public static void write(HttpServletResponse response, ErrorCode code) throws IOException {
        write(response, code.status().value(), code.code());
    }

    /** 상태와 코드를 따로 줄 때 (실험실처럼 enum 밖의 코드) */
    public static void write(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(JSON.writeValueAsString(of(code)));
    }
}
