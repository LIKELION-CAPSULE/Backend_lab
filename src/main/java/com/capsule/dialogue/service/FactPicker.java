package com.capsule.dialogue.service;

import com.capsule.common.settings.RuntimeSettings;
import com.capsule.dialogue.client.Decider;
import com.capsule.dialogue.client.Decider.Decision;
import com.capsule.dialogue.config.DialogueProperties;
import com.capsule.dialogue.dto.ChatInput;
import com.capsule.dialogue.dto.LlmContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 아는 사실 고르기 (ADR 0022 결정 14 · 16): 아는 사실을 답장 프롬프트에 매번 다 넣지 않는다.
 * 결정 모델(Jev 또는 fast LLM)이 사용자 말을 보고 [사실 최대 20개 + 선택 안 함] 중 하나를 고르고, 고른 사실 하나만 답장에 넣는다.
 * 프롬프트가 길어질수록 지시를 덜 따르기 때문이다. 답장보다 먼저 돌아야 해서 짧게 기다리고, 늦거나 실패하면 선택 안 함.
 */
@Component
@RequiredArgsConstructor
public class FactPicker {

    private static final Logger log = LoggerFactory.getLogger(FactPicker.class);
    private static final ExecutorService THREADS = Executors.newVirtualThreadPerTaskExecutor();
    public static final String QUESTION = "fact";
    public static final String NONE = "NONE";

    private final Decider decider;
    private final RuntimeSettings<DialogueProperties> props;

    /** 이번 말에 꺼낼 사실 하나. 없거나 확신이 낮거나 늦으면 빈 값 */
    public Optional<String> pick(ChatInput in, LlmContext ctx) {
        List<String> facts = in.facts();
        if (facts.isEmpty()) return Optional.empty();
        Map<String, String> criteria = new LinkedHashMap<>();
        criteria.put(NONE, "선택 안 함: 사용자의 마지막 말과 자연스럽게 이어지는 사실이 없다 (대부분 이것)");
        for (int i = 0; i < facts.size(); i++) criteria.put("f" + (i + 1), facts.get(i));
        Decider.Question q = new Decider.Question(
                "캐릭터가 사용자의 마지막 말에 답할 때 꺼내면 자연스러운 '사용자에 대해 아는 사실'을 하나 고른다." + " 억지로 이어 붙여야 하면 NONE을 고른다", criteria);
        CompletableFuture<Decision> f = CompletableFuture.supplyAsync(
                () -> decider.decide(
                                ChatReplier.state(in, props.current().signalContextLines()), Map.of(QUESTION, q), ctx)
                        .get(QUESTION),
                THREADS);
        Decision d;
        try {
            // TODO(judgment): J-05 사실 고르기를 기다리는 한도 (llm-budget 안에 답장이 나가야 하므로 짧게, fact-pick-wait)
            d = f.get(props.current().factPickWait().toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            f.cancel(true);
            log.info("아는 사실 고르기 생략 (늦거나 실패): {}", e.toString());
            return Optional.empty();
        }
        if (d == null
                || NONE.equals(d.choice())
                || d.confidence() < props.current().signalMinConfidence()) return Optional.empty();
        int i = Integer.parseInt(d.choice().substring(1)) - 1;
        return i >= 0 && i < facts.size() ? Optional.of(facts.get(i)) : Optional.empty();
    }
}
