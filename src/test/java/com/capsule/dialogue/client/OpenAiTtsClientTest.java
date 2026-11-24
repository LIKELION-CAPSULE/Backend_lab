package com.capsule.dialogue.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capsule.dialogue.client.TtsClient.Speech;
import com.capsule.dialogue.config.LlmProperties;
import com.capsule.dialogue.config.TtsProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
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

/** OpenAI 음성 API 요청 모양과 받은 음성 넘기기 (ADR 0034). 실제 키 없이 로컬 서버로 */
class OpenAiTtsClientTest {

    HttpServer server;
    String requestPath;
    String auth;
    JsonNode requestBody;
    int status = 200;
    byte[] responseBody = new byte[20_000];

    @BeforeEach
    void start() throws IOException {
        for (int i = 0; i < responseBody.length; i++) responseBody[i] = (byte) i;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestPath = exchange.getRequestURI().getPath();
            auth = exchange.getRequestHeaders().getFirst("Authorization");
            requestBody = LlmJson.tree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(status, responseBody.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBody);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    OpenAiTtsClient client(String model) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        LlmProperties llm = new LlmProperties(
                "fake",
                null,
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                0,
                Duration.ofMillis(300),
                false,
                new LlmProperties.Provider(base, "", null, Map.of(), null, null),
                new LlmProperties.Provider(base, "test-key", null, Map.of(), "minimal", null),
                new LlmProperties.Provider(base, "", null, Map.of(), null, null),
                Map.of(),
                Map.of(),
                new LlmProperties.Lab(false, "", 1));
        return new OpenAiTtsClient(
                llm, new TtsProperties("openai", model, "coral", Map.of(), "", 6, Duration.ofSeconds(30)));
    }

    @Test
    void postsSpeechAndRelaysTheMp3InChunks() {
        List<byte[]> chunks = new ArrayList<>();
        client("gpt-4o-mini-tts").speak(new Speech("알지.", "coral", 1.2, "조금 빠르게 말한다."), chunks::add);

        assertThat(requestPath).isEqualTo("/v1/audio/speech");
        assertThat(auth).isEqualTo("Bearer test-key");
        assertThat(requestBody.get("model").asString()).isEqualTo("gpt-4o-mini-tts");
        assertThat(requestBody.get("input").asString()).isEqualTo("알지.");
        assertThat(requestBody.get("voice").asString()).isEqualTo("coral");
        assertThat(requestBody.get("response_format").asString()).isEqualTo("mp3");
        // gpt-4o-mini-tts는 speed 대신 instructions
        assertThat(requestBody.has("speed")).isFalse();
        assertThat(requestBody.get("instructions").asString()).isEqualTo("조금 빠르게 말한다.");

        assertThat(chunks).hasSizeGreaterThan(1); // 다 모으지 않고 나눠 넘김
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        chunks.forEach(all::writeBytes);
        assertThat(all.toByteArray()).isEqualTo(responseBody);
    }

    @Test
    void theMatesModelWinsOverTheSetting() {
        // 메이트 목소리의 모델 (ADR 0036): 설정이 gpt-4o-mini-tts여도 tts-1이면 speed 규칙까지 그 모델 기준
        client("gpt-4o-mini-tts").speak(new Speech("알지.", "nova", 0.85, "무시됨", "tts-1"), b -> {});
        assertThat(requestBody.get("model").asString()).isEqualTo("tts-1");
        assertThat(requestBody.get("voice").asString()).isEqualTo("nova");
        assertThat(requestBody.get("speed").asDouble()).isEqualTo(0.85);
        assertThat(requestBody.has("instructions")).isFalse();
    }

    @Test
    void tts1ModelsGetSpeed() {
        client("tts-1").speak(new Speech("알지.", "coral", 0.85, "무시됨"), b -> {});
        assertThat(requestBody.get("speed").asDouble()).isEqualTo(0.85);
        assertThat(requestBody.has("instructions")).isFalse();
    }

    @Test
    void httpErrorIsAnLlmCallException() {
        status = 401;
        responseBody = "{\"error\":\"bad key\"}".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> client("gpt-4o-mini-tts").speak(new Speech("알지.", "coral", 1, null), b -> {}))
                .isInstanceOf(LlmCallException.class);
    }

    @Test
    void fakeReturnsSilentMp3FramesSizedByText() {
        List<byte[]> chunks = new ArrayList<>();
        new FakeTtsClient().speak(new Speech("알지.", "coral", 1, null), chunks::add);
        assertThat(chunks).hasSize(1);
        byte[] audio = chunks.get(0);
        assertThat(audio.length % 417).isZero();
        assertThat(audio.length / 417).isEqualTo(12); // 3글자 × 4프레임
        assertThat(audio[0]).isEqualTo((byte) 0xFF); // mp3 프레임 머리
        assertThat(audio[1]).isEqualTo((byte) 0xFB);
    }
}
