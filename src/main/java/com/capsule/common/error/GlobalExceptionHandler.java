package com.capsule.common.error;

import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 검증 애너테이션의 message를 명세 에러 코드로 쓴다. 예: @NotBlank(message = "INVALID_EMAIL") */
    private static final Pattern ERROR_CODE = Pattern.compile("[A-Z][A-Z0-9_]*");

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(new ErrorResponse(e.code(), e.code(), e.detail()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .filter(m -> m != null && ERROR_CODE.matcher(m).matches())
                .findFirst()
                .orElse("INVALID_REQUEST");
        return ResponseEntity.badRequest().body(ErrorResponse.of(message));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorResponse> unreadable(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of("INVALID_REQUEST"));
    }

    /**
     * 나머지도 B13 모양으로. 스프링이 상태를 정해 둔 예외(없는 경로 404, 메서드 405 등)는 그 상태 그대로,
     * 그 밖은 500 INTERNAL_ERROR (내용은 로그에만).
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse known) {
            HttpStatus status = HttpStatus.valueOf(known.getStatusCode().value());
            String code = status == HttpStatus.BAD_REQUEST ? "INVALID_REQUEST" : status.name();
            return ResponseEntity.status(status).body(ErrorResponse.of(code));
        }
        log.error("처리하지 못한 예외", e);
        return ResponseEntity.internalServerError().body(ErrorResponse.of("INTERNAL_ERROR"));
    }
}
