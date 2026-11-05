package com.capsule.dialogue.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 공급자 HTTP 공통. Spring RestClient + JDK HttpClient라 의존성을 더하지 않는다 (ADR 0022). */
final class LlmHttp {
    private LlmHttp() {}

    @FunctionalInterface
    interface BodyReader<T> {
        T read(BufferedReader body) throws IOException;
    }

    static RestClient client(String baseUrl, Duration connectTimeout, Duration timeout, Consumer<HttpHeaders> headers) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(timeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeaders(headers)
                .build();
    }

    /** JSON을 POST하고 본문을 reader로 읽는다. 4xx·5xx·연결 실패·형식 오류는 모두 LlmCallException */
    static <T> T post(RestClient http, String provider, String path, Object body, BodyReader<T> reader) {
        try {
            return http.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(LlmJson.write(body))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        try (BufferedReader in =
                                new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            if (status >= 400) throw LlmCallException.http(provider, status, readAll(in));
                            return reader.read(in);
                        }
                    });
        } catch (LlmCallException e) {
            throw e;
        } catch (RestClientException e) {
            // 응답 시간 초과는 다시 보내도 늦을 가능성이 커서 재시도하지 않는다
            if (isTimeout(e)) throw LlmCallException.timeout(provider, e);
            throw LlmCallException.io(provider, e);
        } catch (RuntimeException e) {
            throw LlmCallException.unexpected(provider, e);
        }
    }

    static String readAll(BufferedReader in) {
        return in.lines().collect(Collectors.joining("\n"));
    }

    /** SSE 본문에서 data: 줄만 꺼낸다. OpenAI의 [DONE]에서 끝 */
    static void forEachData(BufferedReader in, Consumer<String> onData) throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            if (!line.startsWith("data:")) continue;
            String data = line.substring(5).trim();
            if (data.equals("[DONE]")) return;
            if (!data.isEmpty()) onData.accept(data);
        }
    }

    static long msSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause())
            if (t instanceof HttpTimeoutException || t instanceof java.net.SocketTimeoutException) return true;
        return false;
    }
}
