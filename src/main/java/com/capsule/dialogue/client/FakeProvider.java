package com.capsule.dialogue.client;

import com.capsule.dialogue.dto.ProviderRequest;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/**
 * 결정적인 가짜 공급자 (같은 입력이면 같은 답, 비용 0). API 키 없이 로컬·테스트·E2E에서 전체 흐름이 돌도록
 * 용도마다 실제 응답과 같은 형식(JSON 등)으로 답한다. 품질은 보지 않는다.
 */
@Component
public class FakeProvider implements LlmProvider {

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public boolean ready() {
        return true;
    }

    @Override
    public Llm.Result complete(ProviderRequest r, Consumer<String> onText) {
        String last = r.messages().isEmpty() ? "" : r.messages().getLast().text();
        String text = switch (r.purpose()) {
            case CHAT ->
                LlmJson.write(Map.of("reply", "「" + head(last, 20) + "」… 그렇구나. 한 문제만 더 해보자.", "emotion", "NEUTRAL"));
            case REACTION -> LlmJson.write(Map.of("content", "(가짜 반응) " + head(last, 30), "emotion", "NEUTRAL"));
            case SUMMARY -> "(가짜 요약) " + head(last, 60);
            case MEMORY -> "(가짜 기억) " + head(last, 20);
            case CLOSING -> "(가짜 완료 대사) 오늘 공부 수고했어."; // 대체 문구(fixed.lines)와 달라야 테스트가 둘을 구분한다
            case EVAL_JUDGE -> "{\"score\":7,\"reason\":\"가짜 심사\"}";
            case DECIDE -> firstChoices(last);
        };
        if (onText != null) onText.accept(text);
        int input = length(r.system().stream().map(Llm.SystemBlock::text).toList())
                + length(r.messages().stream().map(Llm.ChatTurn::text).toList());
        return new Llm.Result(
                text,
                new Llm.Usage(input / 2, 0, 0, text.length() / 2),
                onText != null ? 0L : null,
                0,
                "end_turn",
                false);
    }

    /** 결정: 질문마다 첫 보기를 확신도 0.9로 (신호 질문이면 첫 보기 NEUTRAL → 호감도 0) */
    private static String firstChoices(String payload) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        LlmJson.tree(payload).path("questions").properties().forEach(q -> {
            String first =
                    q.getValue().path("criteria").propertyNames().iterator().next();
            out.put(q.getKey(), Map.of("choice", first, "confidence", 0.9));
        });
        return LlmJson.write(out);
    }

    private static String head(String s, int codePoints) {
        return s.codePoints()
                .limit(codePoints)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private static int length(List<String> texts) {
        return texts.stream().mapToInt(String::length).sum();
    }
}
