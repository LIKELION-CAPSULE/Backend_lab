package com.capsule.dialogue.client;

import static com.capsule.dialogue.client.LlmJson.num;
import static com.capsule.dialogue.client.LlmJson.str;

import com.capsule.dialogue.config.LlmProperties;
import com.capsule.dialogue.dto.ProviderRequest;
import com.capsule.dialogue.engine.Llm;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Anthropic Messages API (POST /v1/messages). 시스템 블록의 cache=true 지점에 cache_control을 달아 프롬프트 캐시를 쓴다.
 * 입력 토큰은 캐시에 없던 것(input_tokens)과 캐시 쓰기·읽기를 따로 받는다.
 */
@Component
class AnthropicProvider implements LlmProvider {

    private final LlmProperties.Provider settings;
    private final RestClient http;

    AnthropicProvider(LlmProperties props) {
        this.settings = props.anthropic();
        this.http = LlmHttp.client(settings.baseUrl(), props.httpTimeout(), h -> {
            h.set("x-api-key", settings.hasKey() ? settings.apiKey() : "");
            h.set("anthropic-version", settings.version());
        });
    }

    @Override
    public String name() {
        return "anthropic";
    }

    @Override
    public boolean ready() {
        return settings.hasKey();
    }

    @Override
    public Llm.Result complete(ProviderRequest r, Consumer<String> onText) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", r.model());
        body.put("max_tokens", r.maxTokens());
        if (!r.system().isEmpty())
            body.put("system", r.system().stream().map(AnthropicProvider::block).toList());
        body.put(
                "messages",
                r.messages().stream()
                        .map(m -> Map.of("role", m.role(), "content", m.text()))
                        .toList());
        if (r.temperature() != null) body.put("temperature", r.temperature());
        long t0 = System.nanoTime();

        if (onText == null) {
            JsonNode res = LlmHttp.post(http, name(), "/v1/messages", body, in -> LlmJson.tree(LlmHttp.readAll(in)));
            StringBuilder text = new StringBuilder();
            boolean thinking = false;
            for (JsonNode c : res.path("content")) {
                String type = str(c.path("type"));
                if ("text".equals(type)) text.append(str(c.path("text")));
                else if (type != null && type.contains("thinking")) thinking = true;
            }
            return new Llm.Result(
                    text.toString(),
                    usage(res.path("usage")),
                    null,
                    LlmHttp.msSince(t0),
                    str(res.path("stop_reason")),
                    thinking);
        }

        body.put("stream", true);
        return LlmHttp.post(http, name(), "/v1/messages", body, in -> {
            StringBuilder text = new StringBuilder();
            Llm.Usage[] usage = {Llm.Usage.ZERO};
            Long[] ttft = {null};
            String[] stop = {null};
            boolean[] thinking = {false};
            LlmHttp.forEachData(in, data -> {
                JsonNode ev = LlmJson.tree(data);
                switch (String.valueOf(str(ev.path("type")))) {
                    case "message_start" -> usage[0] = usage(ev.path("message").path("usage"));
                    case "content_block_start" -> {
                        String type = str(ev.path("content_block").path("type"));
                        if (type != null && type.contains("thinking")) thinking[0] = true;
                    }
                    case "content_block_delta" -> {
                        if ("text_delta".equals(str(ev.path("delta").path("type")))) {
                            if (ttft[0] == null) ttft[0] = LlmHttp.msSince(t0);
                            text.append(str(ev.path("delta").path("text")));
                            onText.accept(text.toString());
                        }
                    }
                    case "message_delta" -> {
                        stop[0] = str(ev.path("delta").path("stop_reason"));
                        Llm.Usage u = usage[0];
                        int out = num(ev.path("usage").path("output_tokens"));
                        usage[0] = new Llm.Usage(u.input(), u.cacheWrite(), u.cacheRead(), Math.max(out, u.output()));
                    }
                    case "error" -> {
                        String type = str(ev.path("error").path("type"));
                        String message = str(ev.path("error").path("message"));
                        // 스트림 중 과부하(overloaded_error)는 HTTP 529와 같게 본다
                        throw LlmCallException.http(
                                name(), "overloaded_error".equals(type) ? 529 : 500, type + ": " + message);
                    }
                    default -> {} // ping, content_block_stop, message_stop
                }
            });
            return new Llm.Result(text.toString(), usage[0], ttft[0], LlmHttp.msSince(t0), stop[0], thinking[0]);
        });
    }

    private static Map<String, Object> block(Llm.SystemBlock b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "text");
        m.put("text", b.text());
        if (b.cache()) m.put("cache_control", Map.of("type", "ephemeral"));
        return m;
    }

    private static Llm.Usage usage(JsonNode u) {
        return new Llm.Usage(
                num(u.path("input_tokens")),
                num(u.path("cache_creation_input_tokens")),
                num(u.path("cache_read_input_tokens")),
                num(u.path("output_tokens")));
    }
}
