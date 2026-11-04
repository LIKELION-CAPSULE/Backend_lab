package com.capsule.dialogue.client;

import com.capsule.dialogue.engine.Engine;
import com.capsule.dialogue.engine.Planning;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * LLM 입출력 JSON. 감독·심사 파서는 snake_case(followup_target 등)로 답하므로 이름 규칙을 맞춘다. 실패하면 예외 → 엔진이 null로 처리.
 * JSON은 공급자 요청 본문, LLM이 낸 JSON 답, 실험실 jsonb 칸에도 쓴다.
 */
public final class LlmJson {
    private LlmJson() {}

    private static final JsonMapper SNAKE = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public static Planning.DirectorPlan plan(String json) {
        return SNAKE.readValue(json, Planning.DirectorPlan.class);
    }

    public static Engine.Verdict verdict(String json) {
        return SNAKE.readValue(json, Engine.Verdict.class);
    }

    public static String write(Object value) {
        return JSON.writeValueAsString(value);
    }

    public static <T> T read(String json, Class<T> type) {
        return json == null ? null : JSON.readValue(json, type);
    }

    public static JsonNode tree(String json) {
        return JSON.readTree(json);
    }

    /** 문자열 값. 없거나 null이면 null */
    public static String str(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull() ? null : n.asString();
    }

    /** 정수 값. 없거나 숫자가 아니면 0 */
    public static int num(JsonNode n) {
        return n == null || !n.isNumber() ? 0 : n.asInt();
    }

    /** LLM 답에서 첫 '{'부터 마지막 '}'까지를 JSON으로 읽는다 (앞뒤 설명·코드 펜스 무시). 없거나 깨졌으면 null */
    public static JsonNode objectIn(String text) {
        if (text == null) return null;
        int a = text.indexOf('{'), b = text.lastIndexOf('}');
        if (a < 0 || b <= a) return null;
        try {
            JsonNode node = JSON.readTree(text.substring(a, b + 1));
            return node.isObject() ? node : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
