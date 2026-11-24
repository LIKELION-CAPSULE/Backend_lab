package com.capsule.dialogue.client;

import com.capsule.dialogue.config.LlmProperties;
import com.capsule.dialogue.config.TtsProperties;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * OpenAI 음성 API (POST /audio/speech, ADR 0034). 응답 본문(mp3)을 다 기다리지 않고 받는 대로 넘긴다.
 * 키 · 주소는 LLM의 openai 설정과 같다 (OPENAI_API_KEY). 유료: 운영 설정으로 켰을 때만 부른다
 */
public class OpenAiTtsClient implements TtsClient {

    /** 이 크기만큼 모이면 넘긴다 (너무 잘게 보내면 SSE 이벤트가 많아짐) */
    private static final int CHUNK_BYTES = 8 * 1024;

    private final RestClient http;
    private final TtsProperties props;

    public OpenAiTtsClient(LlmProperties llm, TtsProperties props) {
        LlmProperties.Provider settings = llm.openai();
        this.props = props;
        this.http = LlmHttp.client(settings.baseUrl(), llm.connectTimeout(), llm.httpTimeout(), h -> {
            if (settings.hasKey()) h.setBearerAuth(settings.apiKey());
        });
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public void speak(Speech speech, Consumer<byte[]> onChunk) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.model());
        body.put("input", speech.text());
        body.put("voice", speech.voice());
        body.put("response_format", FORMAT);
        // gpt-4o-mini-tts는 speed 대신 instructions로 말투를 받는다. tts-1 계열만 speed
        if (props.model().startsWith("tts-1")) body.put("speed", speech.speed());
        else if (speech.instructions() != null && !speech.instructions().isBlank())
            body.put("instructions", speech.instructions());
        try {
            http.post()
                    .uri("/audio/speech")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(LlmJson.write(body))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        try (InputStream in = response.getBody()) {
                            if (status >= 400) {
                                throw LlmCallException.http(
                                        name(),
                                        status,
                                        new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                            }
                            relay(in, onChunk);
                        }
                        return null;
                    });
        } catch (LlmCallException e) {
            throw e;
        } catch (RestClientException e) {
            throw LlmHttp.isTimeout(e) ? LlmCallException.timeout(name(), e) : LlmCallException.io(name(), e);
        }
    }

    /** 받은 만큼 CHUNK_BYTES씩 모아 넘긴다. 마지막 남은 것도 */
    static void relay(InputStream in, Consumer<byte[]> onChunk) throws IOException {
        byte[] buf = new byte[CHUNK_BYTES];
        int filled = 0;
        int n;
        while ((n = in.read(buf, filled, buf.length - filled)) != -1) {
            filled += n;
            if (filled == buf.length) {
                onChunk.accept(buf.clone());
                filled = 0;
            }
        }
        if (filled > 0) onChunk.accept(Arrays.copyOf(buf, filled));
    }
}
