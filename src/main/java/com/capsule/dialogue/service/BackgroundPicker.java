package com.capsule.dialogue.service;

import com.capsule.common.settings.RuntimeSettings;
import com.capsule.content.api.BackgroundQuery;
import com.capsule.content.api.ContentTypes.BackgroundInfo;
import com.capsule.dialogue.client.Decider;
import com.capsule.dialogue.client.Decider.Decision;
import com.capsule.dialogue.config.DialogueProperties;
import com.capsule.dialogue.dto.LlmContext;
import com.capsule.session.api.ConnectionBackgrounds;
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
 * 배경은 대화 흐름에 따라 결정 모델이 고른다 (ADR 0032, 명세 20 '배경'). 표정 고르기(ExpressionPicker)와 같은 방식:
 * 답장을 다 쓴 뒤 "그대로" 또는 메이트 배경 중 하나. 보기가 그 목록뿐이라 목록 밖 배경은 나오지 않는다.
 */
// TODO(judgment): J-18 ① 메이트별 배경 목록 · guide 문구(데이터) ② 너무 자주 바뀌지 않게 할 규칙. 지금은 매 답장 고르고 제한 없음
@Component
@RequiredArgsConstructor
public class BackgroundPicker {

    private static final Logger log = LoggerFactory.getLogger(BackgroundPicker.class);
    private static final ExecutorService THREADS = Executors.newVirtualThreadPerTaskExecutor();
    public static final String QUESTION = "background";
    /** 보기 "그대로" (배경 code와 겹치지 않게 대문자) */
    public static final String KEEP = "KEEP";

    private final Decider decider;
    private final BackgroundQuery backgrounds;
    private final ConnectionBackgrounds connectionBackgrounds;
    private final RuntimeSettings<DialogueProperties> props;

    /**
     * 이 답장으로 배경을 바꿀지. 바꾸면 새 배경, 그대로 · 배경 2장 미만 · 늦거나(fact-pick-wait) 실패하면 빈 값.
     * 저장은 하지 않는다 (답장 저장 트랜잭션에서 {@link #save})
     *
     * @param said 사용자가 방금 한 말
     */
    public Optional<BackgroundInfo> pick(
            long connectionId, long mateId, String speaker, String said, String reply, LlmContext ctx) {
        if (reply == null || reply.isBlank()) return Optional.empty();
        List<BackgroundInfo> all = backgrounds.backgrounds(mateId);
        if (all.size() < 2) return Optional.empty();
        BackgroundInfo now = backgrounds
                .current(mateId, connectionBackgrounds.current(connectionId))
                .orElse(null);
        if (now == null) return Optional.empty();

        Map<String, String> choices = new LinkedHashMap<>();
        choices.put(KEEP, "장소 · 장면이 바뀌지 않음 (대부분은 이것). 지금 배경: " + describe(now));
        for (BackgroundInfo b : all) {
            if (!b.code().equals(now.code())) choices.put(b.code(), describe(b));
        }
        String state = "지금 배경: " + now.name() + "\n사용자: " + (said == null ? "" : said) + "\n" + speaker + ": " + reply;
        Decider.Question q = new Decider.Question(
                speaker + "와 사용자가 있는 장소 · 장면이 이 대화로 바뀌었는지 고른다. 실제로 그곳으로 옮겼거나 장면이 바뀐 게 분명할 때만"
                        + " 그 배경을, 그 장소 이야기만 하거나 애매하면 KEEP",
                choices);
        CompletableFuture<Decision> f = CompletableFuture.supplyAsync(
                () -> decider.decide(state, Map.of(QUESTION, q), ctx.withDeadline(null))
                        .get(QUESTION),
                THREADS);
        try {
            Decision d = f.get(props.current().factPickWait().toMillis(), TimeUnit.MILLISECONDS);
            if (d == null || KEEP.equals(d.choice())) return Optional.empty();
            return all.stream().filter(b -> b.code().equals(d.choice())).findFirst();
        } catch (Exception e) {
            f.cancel(true);
            log.info("배경 고르기 생략 (늦거나 실패): {}", e.toString());
            return Optional.empty();
        }
    }

    /** 답장 저장 트랜잭션 안에서: 연결의 지금 배경을 바꾼다 */
    public void save(long connectionId, BackgroundInfo background) {
        connectionBackgrounds.change(connectionId, background.code());
    }

    private static String describe(BackgroundInfo b) {
        return b.guide() == null || b.guide().isBlank() ? b.name() : b.name() + " - " + b.guide();
    }
}
