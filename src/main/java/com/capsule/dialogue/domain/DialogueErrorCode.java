package com.capsule.dialogue.domain;

import com.capsule.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** dialogue 모듈 에러 코드 (api.md 19, 20, 28 + 실험실) */
public enum DialogueErrorCode implements ErrorCode {
    // 대화 (api.md 19, 20, 28)
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND),
    SESSION_ENDED(HttpStatus.CONFLICT),
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    CHARACTER_NOT_IN_SESSION(HttpStatus.BAD_REQUEST),
    // 실험실 (/internal/llm-lab)
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED),
    CHARACTER_NOT_FOUND(HttpStatus.NOT_FOUND),
    CHARACTER_REQUIRED(HttpStatus.BAD_REQUEST),
    CHARACTER_ID_MISMATCH(HttpStatus.BAD_REQUEST),
    CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    SETTING_NOT_FOUND(HttpStatus.NOT_FOUND),
    SETTINGS_READ_ONLY(HttpStatus.FORBIDDEN),
    UNKNOWN_PROMPT_KEY(HttpStatus.BAD_REQUEST),
    UNKNOWN_KIND(HttpStatus.BAD_REQUEST),
    UNKNOWN_PIPELINE(HttpStatus.BAD_REQUEST),
    EMPTY_PROMPT(HttpStatus.BAD_REQUEST),
    INVALID_YAML(HttpStatus.BAD_REQUEST),
    PROMPT_VERSION_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** 고정(pin)한 버전이 없거나 다른 키 → 요청이 틀린 것이라 400 (코드는 위와 같음) */
    PINNED_PROMPT_VERSION_NOT_FOUND(HttpStatus.BAD_REQUEST, "PROMPT_VERSION_NOT_FOUND"),
    LAB_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND),
    LAB_TURN_NOT_FOUND(HttpStatus.NOT_FOUND),
    EVAL_CASE_NOT_FOUND(HttpStatus.NOT_FOUND),
    EVAL_RUN_NOT_FOUND(HttpStatus.NOT_FOUND),
    EVAL_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND);

    private final HttpStatus status;
    private final String code;

    DialogueErrorCode(HttpStatus status) {
        this(status, null);
    }

    DialogueErrorCode(HttpStatus status, String code) {
        this.status = status;
        this.code = code;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code != null ? code : name();
    }
}
