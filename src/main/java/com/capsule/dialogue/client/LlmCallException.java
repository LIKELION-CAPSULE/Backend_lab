package com.capsule.dialogue.client;

/**
 * LLM 호출 실패. retryable이면 같은 공급자로 다시 시도해 볼 만하다 (429·5xx·529 과부하·연결 오류).
 * refusal은 모델이 안전 이유로 답을 거절한 것: 다시 시도하거나 다른 공급자로 넘기지 않고 대체 대사를 쓴다.
 */
public class LlmCallException extends RuntimeException {

    public static final String REFUSAL = "refusal";

    private final String provider;
    private final Integer status;
    private final boolean retryable;
    private final boolean refusal;

    LlmCallException(
            String provider, Integer status, String message, boolean retryable, boolean refusal, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.status = status;
        this.retryable = retryable;
        this.refusal = refusal;
    }

    static LlmCallException http(String provider, int status, String body) {
        boolean retryable = status == 408 || status == 429 || status >= 500;
        return new LlmCallException(
                provider, status, provider + " HTTP " + status + ": " + abbreviate(body), retryable, false, null);
    }

    static LlmCallException io(String provider, Throwable cause) {
        return new LlmCallException(provider, null, provider + " 연결 실패: " + cause.getMessage(), true, false, cause);
    }

    static LlmCallException deadlinePassed(String provider) {
        return new LlmCallException(
                provider, null, "마감(llm-budget)이 지나 " + provider + " 호출을 시작하지 않음", false, false, null);
    }

    static LlmCallException timeout(String provider, Throwable cause) {
        return new LlmCallException(provider, null, provider + " 응답 시간 초과", false, false, cause);
    }

    static LlmCallException invalid(String provider, String what) {
        return new LlmCallException(provider, null, provider + " 응답 형식 오류: " + abbreviate(what), false, false, null);
    }

    static LlmCallException refusal(String provider) {
        return new LlmCallException(provider, null, provider + " 답변 거절 (" + REFUSAL + ")", false, true, null);
    }

    static LlmCallException unexpected(String provider, RuntimeException cause) {
        return new LlmCallException(provider, null, provider + " 처리 오류: " + cause, false, false, cause);
    }

    public String provider() {
        return provider;
    }

    public Integer status() {
        return status;
    }

    public boolean retryable() {
        return retryable;
    }

    public boolean refusal() {
        return refusal;
    }

    static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() <= 500 ? s : s.substring(0, 500) + "…";
    }
}
