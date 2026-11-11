package com.capsule.dialogue.service;

import com.capsule.common.settings.RuntimeSettings;
import com.capsule.dialogue.client.Decider;
import com.capsule.dialogue.client.Decider.Decision;
import com.capsule.dialogue.config.DialogueProperties;
import com.capsule.dialogue.dto.CharacterContent;
import com.capsule.dialogue.dto.LlmContext;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 표정은 결정 모델이 고른다 (ADR 0028, 명세 19 · 20): LLM은 대사만 쓰고, 다 쓴 대사를 보고 캐릭터 표정 목록 중 하나를.
 * 보기가 그 목록뿐이라 목록 밖 이름은 나오지 않는다 (Decider가 모르는 보기는 버린다).
 */
@Component
@RequiredArgsConstructor
public class ExpressionPicker {

    private static final Logger log = LoggerFactory.getLogger(ExpressionPicker.class);
    private static final ExecutorService THREADS = Executors.newVirtualThreadPerTaskExecutor();
    public static final String QUESTION = "expression";

    private final Decider decider;
    private final RuntimeSettings<DialogueProperties> props;

    /**
     * 이 대사의 표정. 목록이 없거나, 늦거나(fact-pick-wait, 사실 고르기와 같은 한도), 실패하면 null (표정을 바꾸지 않음).
     * 답장의 마감(llm-budget)과 따로 기다린다: 스트리밍(28)은 첫 대사 뒤 마감을 넘겨 끝날 수 있다
     *
     * @param said 사용자가 방금 한 말 (상태 반응이면 일어난 일). 없으면 null
     */
    public String pick(CharacterContent c, String said, String reply, LlmContext ctx) {
        if (c.expressions().isEmpty() || reply == null || reply.isBlank()) return null;
        String state = (said == null || said.isBlank() ? "" : "사용자: " + said + "\n") + c.speaker() + ": " + reply;
        Decider.Question q = new Decider.Question(
                c.speaker() + "가 이 대사를 말하는 순간의 얼굴 표정을 하나 고른다. 말투가 아니라 이 대사에서 드러나는 감정"
                        + "(칭찬받음 · 챙김 · 놀람 · 졸림 · 진지함 · 서운함 · 화)을 본다",
                c.expressionChoices());
        CompletableFuture<Decision> f = CompletableFuture.supplyAsync(
                () -> decider.decide(state, Map.of(QUESTION, q), ctx.withDeadline(null))
                        .get(QUESTION),
                THREADS);
        try {
            Decision d = f.get(props.current().factPickWait().toMillis(), TimeUnit.MILLISECONDS);
            return d == null ? null : d.choice();
        } catch (Exception e) {
            f.cancel(true);
            log.info("표정 고르기 생략 (늦거나 실패): {}", e.toString());
            return null;
        }
    }
}
