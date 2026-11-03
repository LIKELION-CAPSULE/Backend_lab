package com.capsule.dialogue.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.capsule.dialogue.config.ContentLoader;
import com.capsule.dialogue.engine.Analysis.Craft;
import com.capsule.dialogue.engine.Analysis.Signals;
import com.capsule.dialogue.engine.Content.Beat;
import com.capsule.dialogue.engine.Content.WorldEvent;
import com.capsule.dialogue.engine.DialogueState.TurnSummary;
import com.capsule.dialogue.engine.Planning.SubPlan;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * v5 HTML 콘솔(JS)에서 뽑은 기준 출력과 Java 엔진이 같은지 확인한다(Spring 컨텍스트 없음, 빠름).
 * 기준: src/test/resources/dialogue/parity-fixtures.json. 콘텐츠: classpath:characters/malfoy/.
 * 콘텐츠나 규칙을 의도적으로 바꾸면 이 테스트가 깨지는 게 정상이다. 그때는 기대값을 갱신하고 이유를 PR에 적을 것.
 */
class EngineParityTest {

    record RngFx(long seed, List<Double> first5, Map<String, Long> hashes) {}

    record Motif(String id, int count) {}

    record SignalRow(String text, List<String> on, Motif motif, double engagement, List<String> prefs) {}

    record CraftFx(double qRate, String q, int rhetStreak, int deflects, List<String> firsts) {}

    record ProbeFx(CraftFx craft, List<String> beats, List<String> directives) {}

    record PenaltyFx(String reply, String tag, String ask, List<List<Object>> out) {}

    record SplitOut(String tag, String text) {}

    record SplitFx(@JsonProperty("in") String input, SplitOut out) {}

    record Fixtures(
            RngFx rng,
            Map<String, List<SignalRow>> signals,
            ProbeFx probeFinal,
            List<PenaltyFx> penalties,
            List<String> penaltyPrefs,
            List<SplitFx> splitTag,
            List<String> lowtalkEvents) {}

    static Content.Character C;
    static Fixtures F;

    /** 실제 테스트 로그(v3 Sonnet 5.5 세션). 마지막 턴 지시문 비교에 쓴다. */
    static final String[][] PROBE = {
        {"근데 나비에 스톡홀름 방정식 설명해줘", ""},
        {"뭐야", "뭘 놀라, 생각 좀 했거든"},
        {"LLM 동작원리", "그런 수업이 호그와트에 있었나? …힌트 하나만 줄게. 다음에 올 단어를 맞히는 것부터 찾아봐."},
        {"요즘 AI 추세 설명", "하… 그건 머글 쪽 얘기잖아. 난 몰라. 궁금하면 직접 찾아보든가, 난 에세이 써야 하거든."},
        {"그럼 내가 스포하나 해줄까? 해리포터가 죽었어", "설마 그걸 스포라고 가져온 거야? 낚시가 서투르네. 난 안 속거든."},
        {"이건 구라고 볼트모트가 해리포트한테 주겅", "자백은 빠르네. 구라 치고 또 구라 치고? 그 이름 함부로 부르지 마. 공부나 해."},
        {"볼드모트 볼드모트 볼드모트", "주문 외우는 거야? 세 번 부른다고 나타날 것 같아? 사서 선생님이 먼저 오겠다."}
    };

    @BeforeAll
    static void load() throws Exception {
        C = ContentLoader.fromClasspath("malfoy").character();
        try (InputStream in =
                EngineParityTest.class.getClassLoader().getResourceAsStream("dialogue/parity-fixtures.json")) {
            F = JsonMapper.builder()
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build()
                    .readValue(in, Fixtures.class);
        }
    }

    @Test
    void rngMatchesJsBitForBit() {
        F.rng().hashes().forEach((k, v) -> assertThat(Rng.hash(k)).as(k).isEqualTo(v));
        int[] s = {Rng.seed("real_probe")};
        for (double exp : F.rng().first5()) assertThat(Rng.next(s)).isEqualTo(exp);
    }

    @Test
    void userSignalsMatchForAllGoldenScripts() {
        F.signals().forEach((script, rows) -> {
            DialogueState st = DialogueState.start("malfoy", "도윤", 0);
            for (int i = 0; i < rows.size(); i++) {
                SignalRow row = rows.get(i);
                Signals s = Analysis.analyzeUser(st, row.text(), C);
                String at = script + "#" + i + " \"" + row.text() + "\"";
                assertThat(s.flags(true))
                        .as(at)
                        .isEqualTo(row.on().stream().sorted().toList());
                assertThat(
                                s.motif() == null
                                        ? null
                                        : s.motif().id() + ":" + s.motif().count())
                        .as(at)
                        .isEqualTo(
                                row.motif() == null
                                        ? null
                                        : row.motif().id() + ":" + row.motif().count());
                assertThat(st.engagement).as(at).isCloseTo(row.engagement(), within(1e-3));
                assertThat(st.prefs).as(at).isEqualTo(row.prefs());
            }
        });
    }

    @Test
    void directivesForRealProbeLogMatchExactly() {
        DialogueState st = DialogueState.start("malfoy", "승희", 0);
        for (String[] t : PROBE) {
            Analysis.analyzeUser(st, t[0], C);
            st.turns.add(new TurnSummary(
                    st.turns.size() + 1, t[0], t[1], "비꼼", "followup", null, null, List.of(), List.of()));
        }
        Signals s = Analysis.analyzeUser(st, "ㅈ됬어 볼드모트 왔어", C);
        Craft cr = Analysis.craft(st);
        List<Beat> beats = Analysis.eligibleBeats(st, C);
        assertThat(cr.qRate()).isCloseTo(F.probeFinal().craft().qRate(), within(1e-9));
        assertThat(cr.q()).isEqualTo(F.probeFinal().craft().q());
        assertThat(cr.rhetStreak()).isEqualTo(F.probeFinal().craft().rhetStreak());
        assertThat(cr.deflects()).isEqualTo(F.probeFinal().craft().deflects());
        assertThat(cr.firsts()).isEqualTo(F.probeFinal().craft().firsts());
        assertThat(beats.stream().map(Beat::id).toList())
                .isEqualTo(F.probeFinal().beats());
        assertThat(Prompts.directives(st, cr, beats, null, s, 1, C))
                .isEqualTo(F.probeFinal().directives());
    }

    @Test
    void penaltiesMatch_withIntentionalNagFix() {
        DialogueState st = DialogueState.start("malfoy", "x", 0);
        Analysis.analyzeUser(st, "뭐 자꾸 공부만 하래?", C);
        Analysis.analyzeUser(st, "뭐 자꾸 흥이래 나 기숙사 안알려줘 ㅗ", C);
        assertThat(st.prefs).isEqualTo(F.penaltyPrefs());
        for (PenaltyFx p : F.penalties()) {
            List<String> exp = new ArrayList<>();
            for (List<Object> o : p.out()) exp.add(o.get(0) + ":" + ((Number) o.get(1)).intValue());
            if (p.reply().equals("그래서 몇 문제 풀었는데?")) exp = List.of(); // v5의 NAG 오탐을 Java에서 고침(HTML도 동기화됨)
            List<String> got =
                    Scoring.penalties(st, p.reply(), p.tag(), new SubPlan("yes_and", "", "", p.ask()), C).stream()
                            .map(x -> x.label() + ":" + x.points())
                            .toList();
            assertThat(got).as(p.reply()).isEqualTo(exp);
        }
    }

    @Test
    void splitTagMatches() {
        for (SplitFx f : F.splitTag()) {
            Scoring.Tagged t = Scoring.splitTag(f.input());
            assertThat(t.tag()).as(f.input()).isEqualTo(f.out().tag());
            assertThat(t.text()).as(f.input()).isEqualTo(f.out().text());
        }
    }

    @Test
    void seededEventsAreReproducible() {
        DialogueState st = DialogueState.start("malfoy", "도윤", 0);
        st.evRngState = Rng.seed("ev:lowtalk");
        List<String> got = new ArrayList<>();
        for (String t : List.of("ㅇㅇ", "공부중", "몰라", "ㅋㅋ", "응", "피곤", "ㅇㅇ", "끝")) {
            Signals s = Analysis.analyzeUser(st, t, C);
            WorldEvent e = Analysis.rollEvent(st, C, 0.3, false, s);
            got.add(e == null ? null : e.id());
            st.turns.add(
                    new TurnSummary(st.turns.size() + 1, t, "x", "비꼼", "gossip", null, null, List.of(), List.of()));
        }
        assertThat(got).isEqualTo(F.lowtalkEvents());
    }
}
