package com.capsule.dialogue.engine;

import static com.capsule.dialogue.engine.Llm.Role.ACTOR;
import static com.capsule.dialogue.engine.Llm.Role.DIRECTOR;
import static com.capsule.dialogue.engine.Llm.Role.JUDGE;
import static org.assertj.core.api.Assertions.assertThat;

import com.capsule.dialogue.client.LlmJson;
import com.capsule.dialogue.config.ContentLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** 한 턴 흐름을 가짜 LLM으로 확인한다 (문구 대조는 EngineParityTest). Spring·DB 없음. */
class EngineTurnTest {

    static final Content.Character C = ContentLoader.fromClasspath("malfoy").character();

    static final String DIRECTOR_PLAN = """
            {"intent":"장난","frame":"in_world","safety":"none",
             "plans":[{"move":"escalate","beat_id":"","scene":"크게 놀란다","ask_type":"none"},
                      {"move":"yes_and","beat_id":"","scene":"올라탄다","ask_type":"followup"},
                      {"move":"gossip","beat_id":"","scene":"","ask_type":"none"}],
             "followup_target":"","callback":"","pay_loop":"","open_loop":"","new_bit":"볼드모트 장난꾼",
             "user_fact":"","user_pref":"","mood":"당황","tension_delta":5,"status_shift":"down","lore":["voldemort"]}""";
    static final String ACTOR_LINE = "[당황] 뭐, 뭐?! …너 진짜 최악이야.";

    final List<Llm.Call> calls = new ArrayList<>();

    /** 역할별로 정해진 답을 주고 호출을 기록하는 가짜 LLM. stream은 앞 3글자 → 전체 순서로 흘린다 */
    Llm.Client fake(Function<Llm.Call, String> script) {
        return new Llm.Client() {
            @Override
            public Llm.Result call(Llm.Call c) {
                calls.add(c);
                return new Llm.Result(script.apply(c), new Llm.Usage(800, 0, 2400, 40), null, 5, "end_turn", false);
            }

            @Override
            public Llm.Result stream(Llm.Call c, Consumer<String> onText) {
                calls.add(c);
                String t = script.apply(c);
                onText.accept(t.substring(0, Math.min(3, t.length())));
                onText.accept(t);
                return new Llm.Result(t, new Llm.Usage(150, 0, 2400, 40), 3L, 6, "end_turn", false);
            }
        };
    }

    static String standard(Llm.Call c) {
        return switch (c.role()) {
            case DIRECTOR -> DIRECTOR_PLAN;
            case ACTOR -> ACTOR_LINE;
            case JUDGE -> "{\"scores\":[6,9,3],\"pick\":2,\"why\":\"더 살아 있음\"}";
        };
    }

    static Engine engine(Llm.Client llm) {
        return new Engine(C, llm, LlmJson::plan, LlmJson::verdict, () -> 600_000L);
    }

    static DialogueState state() {
        DialogueState st = DialogueState.start("malfoy", "승희", 0);
        st.rngState = Rng.seed("turn-test");
        st.evRngState = Rng.seed("ev:turn-test");
        return st;
    }

    @Test
    void fastTurn_streamsTextWithoutTag_andUpdatesState() {
        DialogueState st = state();
        List<String> shown = new ArrayList<>();
        Engine.TurnResult r = engine(fake(EngineTurnTest::standard))
                .runTurn(st, "야 볼드모트 왔어", Engine.Settings.defaults(), false, true, new Engine.Listener() {
                    @Override
                    public void onText(String clean, String emotion) {
                        shown.add(clean);
                    }
                });

        assertThat(calls).extracting(Llm.Call::role).containsExactly(DIRECTOR, ACTOR);
        assertThat(r.emotion()).isEqualTo("당황");
        assertThat(r.replyClean()).isEqualTo("뭐, 뭐?! …너 진짜 최악이야.");
        assertThat(shown).isNotEmpty().allMatch(t -> !t.contains("[")); // 덜 온 표정 태그는 화면에 안 보임
        assertThat(shown.getLast()).isEqualTo(r.replyClean());
        assertThat(r.metrics().directorParsed()).isTrue();
        assertThat(st.turns).hasSize(1);
        assertThat(st.history).hasSize(2);
        assertThat(st.mood).isEqualTo("당황");
        assertThat(st.bits).extracting(DialogueState.Bit::text).contains("볼드모트 장난꾼");
    }

    @Test
    void directorJsonFailure_stillAnswersWithDefaultPlan() {
        DialogueState st = state();
        Engine.TurnResult r = engine(fake(c -> c.role() == DIRECTOR ? "계획을 못 세웠어" : ACTOR_LINE))
                .runTurn(st, "응", Engine.Settings.defaults(), false, false, null);

        assertThat(r.metrics().directorParsed()).isFalse();
        assertThat(r.replyClean()).isEqualTo("뭐, 뭐?! …너 진짜 최악이야.");
        assertThat(r.chosen().move()).isEqualTo("gossip"); // 단답이면 코드가 gossip을 고름 (명세 10장)
        assertThat(st.turns).hasSize(1);
    }

    @Test
    void qualityMode_writesThreeCandidates_andPicksJudgedOne() {
        DialogueState st = state();
        Engine.Settings quality = new Engine.Settings(
                "claude-sonnet-5-5",
                "claude-haiku-5-5",
                "claude-haiku-5-5",
                true,
                true,
                0.3,
                500,
                900,
                700,
                300,
                Llm.DEFAULT_PRICES);
        List<String> stages = new ArrayList<>();
        Engine.TurnResult r = engine(fake(EngineTurnTest::standard))
                .runTurn(st, "야 볼드모트 왔어", quality, false, true, new Engine.Listener() {
                    @Override
                    public void onStage(String stage) {
                        stages.add(stage);
                    }
                });

        assertThat(calls).extracting(Llm.Call::role).containsExactly(DIRECTOR, ACTOR, ACTOR, ACTOR, JUDGE);
        assertThat(stages).containsExactly("후보 3개 쓰는 중", "심사 중");
        assertThat(r.candidates()).hasSize(3);
        assertThat(r.candidates().get(1).judge()).isEqualTo(9.0);
        assertThat(r.judgeWhy()).isEqualTo("더 살아 있음");
        assertThat(r.metrics().costUsd()).isPositive();
    }

    @Test
    void fixedLines_breakResumeAwayBack_withoutLlm() {
        DialogueState st = state();
        Engine e = engine(fake(c -> {
            throw new AssertionError("고정 대사는 LLM을 부르지 않는다");
        }));

        assertThat(e.greet(st)).contains("승희");
        assertThat(e.toggleMode(st)).isEqualTo(C.fixedLines().get("break").getFirst());
        assertThat(st.mode).isEqualTo(DialogueState.BREAK);
        assertThat(e.toggleMode(st)).isEqualTo(C.fixedLines().get("resume").getFirst());
        assertThat(st.mode).isEqualTo(DialogueState.STUDY);

        assertThat(e.away(st)).isEqualTo(C.fixedLines().get("away").getFirst());
        assertThat(e.away(st)).isNull(); // 이미 자리 비움
        assertThat(e.back(st)).isEqualTo(C.fixedLines().get("back").getFirst());
        assertThat(e.back(st)).isNull();
        assertThat(st.pendingFixed).hasSize(5); // 다음 턴 감독·배우에게 전달
    }
}
