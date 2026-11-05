package com.capsule.dialogue.client;

import static com.capsule.dialogue.client.LlmJson.num;
import static com.capsule.dialogue.client.LlmJson.str;

import com.capsule.dialogue.config.LlmProperties;
import com.capsule.dialogue.dto.ProviderRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * OpenAI Chat Completions API (POST /chat/completions). base-url만 바꾸면 같은 형식을 쓰는 다른 서버에도 붙는다.
 * 시스템 블록은 system 메시지 하나로 합친다 (OpenAI는 앞부분이 같으면 자동으로 캐시하므로 cache 표시는 쓰지 않음).
 * prompt_tokens에는 캐시 읽기가 들어 있어서 빼고 input으로 기록한다. 거절(refusal·content_filter)은 stopReason "refusal".
 */
@Component
class OpenAiProvider implements LlmProvider {

    private final LlmProperties.Provider settings;
    private final RestClient http;

    OpenAiProvider(LlmProperties props) {
        this.settings = props.openai();
        this.http = LlmHttp.client(settings.baseUrl(), props.connectTimeout(), props.httpTimeout(), h -> {
            if (settings.hasKey()) h.setBearerAuth(settings.apiKey());
        });
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public boolean ready() {
        return settings.hasKey();
    }

    @Override
    public Llm.Result complete(ProviderRequest r, Consumer<String> onText) {
        List<Map<String, Object>> messages = new ArrayList<>();
        String system = r.system().stream().map(Llm.SystemBlock::text).collect(Collectors.joining("\n\n"));
        if (!system.isEmpty()) messages.add(Map.of("role", "system", "content", system));
        for (Llm.ChatTurn m : r.messages()) messages.add(Map.of("role", m.role(), "content", m.text()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", r.model());
        body.put("messages", messages);
        body.put("max_completion_tokens", r.maxTokens());
        if (r.temperature() != null) body.put("temperature", r.temperature());
        if (r.reasoningEffort() != null && !r.reasoningEffort().isBlank() && reasoningModel(r.model()))
            body.put("reasoning_effort", r.reasoningEffort());
        long t0 = System.nanoTime();

        if (onText == null) {
            JsonNode res =
                    LlmHttp.post(http, name(), "/chat/completions", body, in -> LlmJson.tree(LlmHttp.readAll(in)));
            JsonNode choice = res.path("choices").path(0);
            if (choice.isMissingNode()) throw LlmCallException.invalid(name(), "choices 없음");
            JsonNode message = choice.path("message");
            String text = str(message.path("content"));
            boolean refused = str(message.path("refusal")) != null;
            return new Llm.Result(
                    text == null ? "" : text,
                    usage(res.path("usage")),
                    null,
                    LlmHttp.msSince(t0),
                    stopReason(str(choice.path("finish_reason")), refused),
                    reasoned(res.path("usage")));
        }

        body.put("stream", true);
        body.put("stream_options", Map.of("include_usage", true));
        return LlmHttp.post(http, name(), "/chat/completions", body, in -> {
            StringBuilder text = new StringBuilder();
            JsonNode[] usage = {null};
            Long[] ttft = {null};
            String[] finish = {null};
            boolean[] refused = {false};
            LlmHttp.forEachData(in, data -> {
                JsonNode chunk = LlmJson.tree(data);
                // 스트림 도중의 오류는 HTTP 200 안에 {"error": …} 청크로 온다
                if (chunk.path("error").isObject())
                    throw LlmCallException.http(name(), 500, chunk.path("error").toString());
                JsonNode choice = chunk.path("choices").path(0);
                if (!choice.isMissingNode()) {
                    String delta = str(choice.path("delta").path("content"));
                    if (delta != null && !delta.isEmpty()) {
                        if (ttft[0] == null) ttft[0] = LlmHttp.msSince(t0);
                        text.append(delta);
                        onText.accept(text.toString());
                    }
                    if (str(choice.path("delta").path("refusal")) != null) refused[0] = true;
                    String f = str(choice.path("finish_reason"));
                    if (f != null) finish[0] = f;
                }
                if (chunk.path("usage").isObject()) usage[0] = chunk.path("usage");
            });
            JsonNode u = usage[0] == null ? LlmJson.tree("{}") : usage[0];
            return new Llm.Result(
                    text.toString(),
                    usage(u),
                    ttft[0],
                    LlmHttp.msSince(t0),
                    stopReason(finish[0], refused[0]),
                    reasoned(u));
        });
    }

    /**
     * reasoning_effort를 받는 추론 모델인지. 다른 모델(gpt-4o 등, base-url 호환 서버)에 보내면 400이 난다.
     * ponytail: 모델 이름으로 가른다. 새 추론 계열이 나오면 여기에 더한다
     */
    static boolean reasoningModel(String model) {
        return model != null && (model.startsWith("gpt-5") || model.matches("o\\d.*"));
    }

    private static String stopReason(String finish, boolean refused) {
        return refused || "content_filter".equals(finish) ? LlmCallException.REFUSAL : finish;
    }

    private static Llm.Usage usage(JsonNode u) {
        int prompt = num(u.path("prompt_tokens"));
        int cached = num(u.path("prompt_tokens_details").path("cached_tokens"));
        return new Llm.Usage(Math.max(0, prompt - cached), 0, cached, num(u.path("completion_tokens")));
    }

    private static boolean reasoned(JsonNode u) {
        return num(u.path("completion_tokens_details").path("reasoning_tokens")) > 0;
    }
}
