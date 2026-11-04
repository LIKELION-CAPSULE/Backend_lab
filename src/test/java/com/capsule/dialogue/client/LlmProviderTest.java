package com.capsule.dialogue.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capsule.dialogue.config.LlmProperties;
import com.capsule.dialogue.dto.LlmPurpose;
import com.capsule.dialogue.dto.ProviderRequest;
import com.capsule.dialogue.engine.Llm;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** 공급자 요청·응답 형식을 로컬 가짜 서버로 확인한다 (실제 API·키 없음). Spring·DB 없음 */
class LlmProviderTest {

    HttpServer server;
    String requestPath;
    Map<String, List<String>> requestHeaders;
    JsonNode requestBody;
    int status = 200;
    String contentType = "application/json";
    String responseBody;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestPath = exchange.getRequestURI().getPath();
            requestHeaders = exchange.getRequestHeaders();
            requestBody = LlmJson.tree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    LlmProperties props() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new LlmProperties(
                "anthropic",
                null,
                Duration.ofSeconds(5),
                0,
                false,
                new LlmProperties.Provider(
                        base,
                        "test-key",
                        "2023-06-01",
                        Map.of(),
                        null,
                        Map.of(
                                "claude-haiku-5-5", new LlmProperties.Thinking("disabled", "low"),
                                "claude-sonnet-5-5", new LlmProperties.Thinking("between_tools", "low"))),
                new LlmProperties.Provider(base + "/v1", "test-key", null, Map.of(), "minimal", null),
                new LlmProperties.Provider(base, "test-key", null, Map.of(), null, null),
                Map.of(),
                Map.of(),
                new LlmProperties.Lab(false, "", 1));
    }

    static ProviderRequest request(Double temperature) {
        return request(temperature, null);
    }

    /** reasoningEffort는 게이트웨이가 공급자 설정에서 채워 넣는다 */
    static ProviderRequest request(Double temperature, String reasoningEffort) {
        return request(temperature, reasoningEffort, "model-x");
    }

    static ProviderRequest request(Double temperature, String reasoningEffort, String model) {
        return new ProviderRequest(
                LlmPurpose.CHAT,
                model,
                List.of(new Llm.SystemBlock("캐릭터 규칙", true), new Llm.SystemBlock("지금 상황", false)),
                List.of(
                        new Llm.ChatTurn("user", "안녕"),
                        new Llm.ChatTurn("assistant", "왔어?"),
                        new Llm.ChatTurn("user", "응")),
                300,
                temperature,
                reasoningEffort);
    }

    // --- Anthropic ---

    @Test
    void anthropicSendsCacheControlAndReadsUsage() {
        responseBody = """
                {"content":[{"type":"text","text":"반가워"}],"stop_reason":"end_turn",
                 "usage":{"input_tokens":12,"cache_creation_input_tokens":0,"cache_read_input_tokens":2048,"output_tokens":7}}""";

        Llm.Result r = new AnthropicProvider(props()).complete(request(null), null);

        assertThat(requestPath).isEqualTo("/v1/messages");
        assertThat(requestHeaders.get("X-api-key")).containsExactly("test-key");
        assertThat(requestHeaders.get("Anthropic-version")).containsExactly("2023-06-01");
        assertThat(requestBody.path("model").asString()).isEqualTo("model-x");
        assertThat(requestBody.path("max_tokens").asInt()).isEqualTo(300);
        assertThat(requestBody
                        .path("system")
                        .get(0)
                        .path("cache_control")
                        .path("type")
                        .asString())
                .isEqualTo("ephemeral");
        assertThat(requestBody.path("system").get(1).has("cache_control")).isFalse();
        assertThat(requestBody.path("messages")).hasSize(3);
        assertThat(requestBody.has("temperature")).isFalse();
        assertThat(requestBody.has("stream")).isFalse();
        assertThat(requestBody.has("thinking")).isFalse(); // 생각 설정이 없는 모델은 모델 기본값
        assertThat(requestBody.has("output_config")).isFalse();
        assertThat(r.text()).isEqualTo("반가워");
        assertThat(r.usage()).isEqualTo(new Llm.Usage(12, 0, 2048, 7));
        assertThat(r.stopReason()).isEqualTo("end_turn");
        assertThat(r.ttftMs()).isNull();
    }

    /** 5.5는 생각 설정이 없으면 생각 토큰이 max_tokens를 먹어 빈 답이 난다 (#92). 모델마다 받는 값이 달라서 모델별로 보낸다 */
    @Test
    void anthropicSendsThinkingSettingsPerModel() {
        responseBody = """
                {"content":[{"type":"text","text":"응"}],"stop_reason":"end_turn","usage":{"output_tokens":1}}""";
        AnthropicProvider provider = new AnthropicProvider(props());

        provider.complete(request(null, null, "claude-haiku-5-5"), null);
        assertThat(requestBody.path("thinking").path("type").asString()).isEqualTo("disabled");
        assertThat(requestBody.path("output_config").path("effort").asString()).isEqualTo("low");

        // sonnet-5-5는 disabled가 400: 가장 낮은 설정 between_tools. thinking 안에 다른 칸을 넣으면 400이라 type만
        provider.complete(request(null, null, "claude-sonnet-5-5"), null);
        assertThat(requestBody.path("thinking").path("type").asString()).isEqualTo("between_tools");
        assertThat(requestBody.path("thinking").size()).isEqualTo(1);
        assertThat(requestBody.path("output_config").path("effort").asString()).isEqualTo("low");
    }

    @Test
    void anthropicStreamsTextAndMergesUsage() {
        contentType = "text/event-stream";
        responseBody = """
                event: message_start
                data: {"type":"message_start","message":{"usage":{"input_tokens":20,"cache_creation_input_tokens":1500,"cache_read_input_tokens":0,"output_tokens":1}}}

                event: ping
                data: {"type":"ping"}

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"[당황] 뭐"}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"?!"}}

                event: message_delta
                data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":9}}

                event: message_stop
                data: {"type":"message_stop"}

                """;
        List<String> seen = new ArrayList<>();

        Llm.Result r = new AnthropicProvider(props()).complete(request(0.7), seen::add);

        assertThat(requestBody.path("stream").asBoolean()).isTrue();
        assertThat(requestBody.path("temperature").asDouble()).isEqualTo(0.7);
        assertThat(seen).containsExactly("[당황] 뭐", "[당황] 뭐?!");
        assertThat(r.text()).isEqualTo("[당황] 뭐?!");
        assertThat(r.usage()).isEqualTo(new Llm.Usage(20, 1500, 0, 9));
        assertThat(r.ttftMs()).isNotNull();
        assertThat(r.stopReason()).isEqualTo("end_turn");
    }

    @Test
    void anthropicErrorsSayWhetherToRetry() {
        status = 529;
        responseBody = "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}";
        assertThatThrownBy(() -> new AnthropicProvider(props()).complete(request(null), null))
                .isInstanceOfSatisfying(LlmCallException.class, e -> {
                    assertThat(e.retryable()).isTrue();
                    assertThat(e.status()).isEqualTo(529);
                });

        status = 400;
        responseBody = "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"bad\"}}";
        assertThatThrownBy(() -> new AnthropicProvider(props()).complete(request(null), null))
                .isInstanceOfSatisfying(
                        LlmCallException.class, e -> assertThat(e.retryable()).isFalse());
    }

    // --- OpenAI ---

    @Test
    void openAiJoinsSystemAndSubtractsCachedTokens() {
        responseBody = """
                {"choices":[{"message":{"role":"assistant","content":"반가워","refusal":null},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":2100,"completion_tokens":30,
                          "prompt_tokens_details":{"cached_tokens":2000},"completion_tokens_details":{"reasoning_tokens":16}}}""";

        Llm.Result r = new OpenAiProvider(props()).complete(request(null, "minimal", "gpt-5-mini"), null);

        assertThat(requestPath).isEqualTo("/v1/chat/completions");
        assertThat(requestHeaders.get("Authorization")).containsExactly("Bearer test-key");
        assertThat(requestBody.path("messages").get(0).path("role").asString()).isEqualTo("system");
        assertThat(requestBody.path("messages").get(0).path("content").asString())
                .isEqualTo("캐릭터 규칙\n\n지금 상황");
        assertThat(requestBody.path("messages")).hasSize(4);
        assertThat(requestBody.path("max_completion_tokens").asInt()).isEqualTo(300);
        assertThat(requestBody.path("reasoning_effort").asString()).isEqualTo("minimal");
        assertThat(requestBody.has("temperature")).isFalse();
        assertThat(r.text()).isEqualTo("반가워");
        assertThat(r.usage()).isEqualTo(new Llm.Usage(100, 0, 2000, 30));
        assertThat(r.thinkingSeen()).isTrue();
        assertThat(r.stopReason()).isEqualTo("stop");
    }

    @Test
    void openAiContentFilterIsRefusal() {
        responseBody = """
                {"choices":[{"message":{"role":"assistant","content":""},"finish_reason":"content_filter"}],
                 "usage":{"prompt_tokens":10,"completion_tokens":0}}""";

        assertThat(new OpenAiProvider(props()).complete(request(null), null).stopReason())
                .isEqualTo(LlmCallException.REFUSAL);
    }

    @Test
    void openAiStreamsUntilDone() {
        contentType = "text/event-stream";
        responseBody = """
                data: {"choices":[{"index":0,"delta":{"role":"assistant","content":""},"finish_reason":null}]}

                data: {"choices":[{"index":0,"delta":{"content":"안"},"finish_reason":null}]}

                data: {"choices":[{"index":0,"delta":{"content":"녕"},"finish_reason":"stop"}]}

                data: {"choices":[],"usage":{"prompt_tokens":50,"completion_tokens":2,"prompt_tokens_details":{"cached_tokens":0}}}

                data: [DONE]

                """;
        List<String> seen = new ArrayList<>();

        Llm.Result r = new OpenAiProvider(props()).complete(request(null), seen::add);

        assertThat(requestBody.path("stream_options").path("include_usage").asBoolean())
                .isTrue();
        assertThat(seen).containsExactly("안", "안녕");
        assertThat(r.text()).isEqualTo("안녕");
        assertThat(r.usage()).isEqualTo(new Llm.Usage(50, 0, 0, 2));
        assertThat(r.stopReason()).isEqualTo("stop");
        assertThat(r.ttftMs()).isNotNull();
    }

    @Test
    void openAiSkipsReasoningEffortForNonReasoningModels() {
        responseBody = """
                {"choices":[{"message":{"content":"응"},"finish_reason":"stop"}],"usage":{"prompt_tokens":5,"completion_tokens":1}}""";

        new OpenAiProvider(props()).complete(request(null, "minimal", "gpt-4o-mini"), null);

        assertThat(requestBody.has("reasoning_effort")).isFalse();
        assertThat(OpenAiProvider.reasoningModel("o4-mini")).isTrue();
    }

    // --- Jev (결정 모델) ---

    @Test
    void jevSendsChoiceQuestionsAndReturnsDeciderJson() {
        responseBody = """
                {"model":"jev-1.13.0",
                 "answers":{"signal":{"type":"choice","choice":"PRAISE","confidence":0.82,
                                      "probabilities":{"NEUTRAL":0.1,"PRAISE":0.82,"RUDE":0.08}}},
                 "usage":{"input_tokens":120,"output_tokens":0}}""";
        String payload = """
                {"state":"사용자의 마지막 말: 고마워","questions":{"signal":{"instructions":"신호를 고른다",
                 "criteria":{"NEUTRAL":"평범","PRAISE":"칭찬","RUDE":"무례"}}}}""";
        ProviderRequest req = new ProviderRequest(
                LlmPurpose.DECIDE,
                "jev-1.13.0",
                List.of(new Llm.SystemBlock("분류기", true)),
                List.of(new Llm.ChatTurn("user", payload)),
                200,
                null,
                null);

        Llm.Result r = new JevProvider(props()).complete(req, null);

        assertThat(requestPath).isEqualTo("/v1/systemone");
        assertThat(requestHeaders.get("Authorization")).containsExactly("Bearer test-key");
        assertThat(requestBody.path("model").asString()).isEqualTo("jev-1.13.0");
        assertThat(requestBody.path("state").asString()).isEqualTo("사용자의 마지막 말: 고마워");
        JsonNode q = requestBody.path("questions").path("signal");
        assertThat(q.path("type").asString()).isEqualTo("choice");
        assertThat(q.path("criteria").path("PRAISE").asString()).isEqualTo("칭찬");
        JsonNode out = LlmJson.tree(r.text());
        assertThat(out.path("signal").path("choice").asString()).isEqualTo("PRAISE");
        assertThat(out.path("signal").path("confidence").asDouble()).isEqualTo(0.82);
        assertThat(r.usage()).isEqualTo(new Llm.Usage(120, 0, 0, 0));
    }

    @Test
    void jevRefusesNonDecisionPurposes() {
        assertThatThrownBy(() -> new JevProvider(props()).complete(request(null), null))
                .isInstanceOfSatisfying(
                        LlmCallException.class, e -> assertThat(e.retryable()).isFalse());
    }

    @Test
    void openAiErrorInsideStreamIsAnError() {
        contentType = "text/event-stream";
        responseBody = """
                data: {"choices":[{"index":0,"delta":{"content":"안"},"finish_reason":null}]}

                data: {"error":{"message":"server_error","type":"server_error"}}

                """;

        assertThatThrownBy(() -> new OpenAiProvider(props()).complete(request(null), s -> {}))
                .isInstanceOfSatisfying(
                        LlmCallException.class, e -> assertThat(e.retryable()).isTrue());
    }
}
