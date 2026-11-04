package com.capsule.dialogue.client;

import static com.capsule.dialogue.client.LlmJson.str;

import com.capsule.dialogue.config.LlmProperties;
import com.capsule.dialogue.dto.LlmPurpose;
import com.capsule.dialogue.dto.ProviderRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * TypeSafe Jev 결정 모델 (POST /v1/systemone). 글을 생성하지 않고 질문마다 보기 하나와 확신도를 준다. DECIDE 용도만 받는다.
 * Decider가 보낸 JSON(state + questions)을 Jev의 choice 질문으로 바꾸고, 답을 Decider 형식 {id: {choice, confidence}}로 돌려준다.
 * 요금은 입력 토큰만. 응답의 model(실제 버전)을 stopReason 대신 남길 곳이 없어 모델은 요청에 고정한 버전으로 기록된다.
 * ponytail: choice 질문만 쓴다. score · noul 질문이 필요해지면 Decider.Question에 type을 더할 것
 */
@Component
class JevProvider implements LlmProvider {

    private final LlmProperties.Provider settings;
    private final RestClient http;

    JevProvider(LlmProperties props) {
        this.settings = props.jev();
        this.http = settings == null
                ? null
                : LlmHttp.client(settings.baseUrl(), props.httpTimeout(), h -> {
                    if (settings.hasKey()) h.setBearerAuth(settings.apiKey());
                });
    }

    @Override
    public String name() {
        return "jev";
    }

    @Override
    public boolean ready() {
        return settings != null && settings.hasKey();
    }

    @Override
    public Llm.Result complete(ProviderRequest r, Consumer<String> onText) {
        if (r.purpose() != LlmPurpose.DECIDE || http == null)
            throw LlmCallException.invalid(name(), "결정(DECIDE)만 받는다: " + r.purpose());
        JsonNode payload = LlmJson.tree(r.messages().getLast().text());
        Map<String, Object> questions = new LinkedHashMap<>();
        payload.path("questions")
                .properties()
                .forEach(q -> questions.put(
                        q.getKey(),
                        Map.of(
                                "type",
                                "choice",
                                "instructions",
                                q.getValue().path("instructions").asString(),
                                "criteria",
                                q.getValue().path("criteria"))));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("state", payload.path("state").asString());
        body.put("model", r.model());
        body.put("questions", questions);

        long t0 = System.nanoTime();
        JsonNode res = LlmHttp.post(http, name(), "/v1/systemone", body, in -> LlmJson.tree(LlmHttp.readAll(in)));
        JsonNode answers = res.path("answers");
        if (!answers.isObject()) throw LlmCallException.invalid(name(), "answers 없음");
        Map<String, Object> out = new LinkedHashMap<>();
        answers.properties().forEach(a -> {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("choice", str(a.getValue().path("choice")));
            d.put("confidence", a.getValue().path("confidence").asDouble(0));
            out.put(a.getKey(), d);
        });
        String text = LlmJson.write(out);
        if (onText != null) onText.accept(text);
        JsonNode usage = res.path("usage");
        return new Llm.Result(
                text,
                new Llm.Usage(
                        usage.path("input_tokens").asInt(0),
                        0,
                        0,
                        usage.path("output_tokens").asInt(0)),
                onText != null ? LlmHttp.msSince(t0) : null,
                LlmHttp.msSince(t0),
                "end_turn",
                false);
    }
}
