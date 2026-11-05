package com.capsule.dialogue.client;

import java.util.Map;

/** LLM 호출의 공통 타입 (요청 블록 · 결과 · 사용량 · 가격). 호출은 LlmGateway가 한다. */
public final class Llm {
    private Llm() {}

    /** cache=true인 블록에 프롬프트 캐시 지점을 둔다. 바뀌는 내용은 시스템에 넣지 않는다(캐시가 깨짐). */
    public record SystemBlock(String text, boolean cache) {}

    public record ChatTurn(String role, String text) {}

    public record Usage(int input, int cacheWrite, int cacheRead, int output) {
        public static final Usage ZERO = new Usage(0, 0, 0, 0);

        public int promptTotal() {
            return input + cacheWrite + cacheRead;
        }
    }

    public record Result(
            String text, Usage usage, Long ttftMs, long totalMs, String stopReason, boolean thinkingSeen) {}

    /** 달러/백만 토큰. 캐시 쓰기는 5분 캐시 기준. */
    public record Price(double in, double cacheWrite, double cacheRead, double out) {
        public double cost(Usage u) {
            return (u.input() * in + u.cacheWrite() * cacheWrite + u.cacheRead() * cacheRead + u.output() * out) / 1e6;
        }
    }
    /** Anthropic 공식 가격표(2026-11-01 확인, Sonnet 5.5 캐시 읽기는 2026-11-04 $0.20으로 정정). 설정 파일에서 덮어쓸 수 있게 Spring 쪽에서 주입한다. */
    public static final Map<String, Price> DEFAULT_PRICES = Map.of(
            "claude-haiku-5-5", new Price(0.10, 0.125, 0.01, 0.50),
            "claude-sonnet-5-5", new Price(2, 2.5, 0.20, 10),
            "claude-opus-5-5", new Price(4, 5, 0.20, 20));
}
