package com.capsule.dialogue.client;

import com.capsule.dialogue.engine.Engine;
import com.capsule.dialogue.engine.Planning;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/** 감독·심사 JSON 파서. 감독은 snake_case(followup_target 등)로 답하므로 이름 규칙을 맞춘다. 실패하면 예외 → 엔진이 null로 처리. */
public final class LlmJson {
    private LlmJson() {}

    private static final JsonMapper SNAKE = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public static Planning.DirectorPlan plan(String json) {
        return SNAKE.readValue(json, Planning.DirectorPlan.class);
    }

    public static Engine.Verdict verdict(String json) {
        return SNAKE.readValue(json, Engine.Verdict.class);
    }
}
