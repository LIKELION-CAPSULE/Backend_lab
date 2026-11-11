package com.capsule.dialogue.client;

import com.capsule.dialogue.dto.LlmContext;
import com.capsule.dialogue.dto.LlmPurpose;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * 결정 입구 (ADR 0022): 상태 글 하나 + 보기가 정해진 질문들 → 질문마다 고른 보기와 확신도. 글을 쓰지 않는다.
 * 게이트웨이 DECIDE 용도로 보내므로 공급자는 설정이 정한다: Jev(결정 모델)든 LLM이든 같은 모양으로 답한다.
 * 요청은 user 메시지 하나의 JSON {"state", "questions": {id: {instructions, criteria: {보기: 설명}}}}이고
 * 답은 {id: {"choice", "confidence"}} JSON이다. LLM은 아래 시스템 지시를 따라, Jev 어댑터는 이 JSON을 Jev 형식으로 바꿔 보낸다.
 */
@Component
@RequiredArgsConstructor
public class Decider {

    /** @param criteria 보기 이름 → 설명. 순서를 지킨다 (LinkedHashMap) */
    public record Question(String instructions, Map<String, String> criteria) {}

    /** @param confidence 0~1. 고른 보기를 얼마나 확신하는지 */
    public record Decision(String choice, double confidence) {}

    private static final String SYSTEM = """
            너는 글을 쓰지 않고 결정만 하는 분류기다.
            user 메시지의 JSON에서 state를 읽고, questions의 질문마다 그 질문의 criteria 키 중 정확히 하나를 고른다.
            confidence는 그 보기가 맞을 확률(0~1)이다. 애매하면 낮게 준다.
            JSON 한 줄만 출력한다: {"<질문 id>": {"choice": "<criteria 키>", "confidence": 0.0}, ...}""";

    private final LlmGateway llm;

    /** 형식이 틀린 답(모르는 보기 등)은 빼고 돌려준다. 호출이 실패하면 예외 (부르는 쪽이 기본값으로) */
    public Map<String, Decision> decide(String state, Map<String, Question> questions, LlmContext ctx) {
        String payload = LlmJson.write(Map.of("state", state, "questions", questions));
        Llm.Result r = llm.complete(
                LlmPurpose.DECIDE,
                List.of(new Llm.SystemBlock(SYSTEM, true)),
                List.of(new Llm.ChatTurn("user", payload)),
                ctx,
                null);
        JsonNode json = LlmJson.objectIn(r.text());
        if (json == null) throw new IllegalStateException("결정 JSON이 아님: " + r.text());
        Map<String, Decision> out = new LinkedHashMap<>();
        questions.forEach((id, q) -> {
            JsonNode a = json.path(id);
            String choice = LlmJson.str(a.path("choice"));
            if (choice != null && q.criteria().containsKey(choice)) {
                double confidence =
                        a.path("confidence").isNumber() ? a.path("confidence").asDouble() : 0;
                out.put(id, new Decision(choice, Math.clamp(confidence, 0.0, 1.0)));
            }
        });
        return out;
    }
}
