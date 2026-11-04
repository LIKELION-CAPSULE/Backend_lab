package com.capsule.dialogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.TestcontainersConfiguration;
import com.capsule.dialogue.client.LlmJson;
import com.capsule.dialogue.config.CharacterContentSeeder;
import com.capsule.dialogue.dto.CharacterContent;
import com.capsule.dialogue.dto.CharacterContent.Lore;
import com.capsule.dialogue.dto.CharacterContent.World;
import com.capsule.dialogue.repository.CharacterContentRepository;
import com.capsule.dialogue.service.CharacterContents;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 캐릭터 · 세계관 설정이 DB에 있고(처음 값 = characters/malfoy), 대사가 그 데이터로만 돈다 (ADR 0022 결정 15) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CharacterContentTest {

    static final String API = "/internal/llm-lab/api/characters";

    @Autowired
    CharacterContents contents;

    @Autowired
    MockMvc mvc;

    @Autowired
    CharacterContentRepository repository;

    @Autowired
    CharacterContentSeeder seeder;

    @Test
    void malfoyIsSeededIntoTheDatabase() {
        CharacterContent c = contents.find(1).orElseThrow();
        assertThat(c.speaker()).isEqualTo("말포이");
        assertThat(c.safety()).isEqualTo(CharacterContent.MINOR);
        assertThat(c.expressions()).containsExactly("삐짐", "행복", "경멸", "하품", "화남", "웃음", "수줍음", "집중");
        assertThat(c.world().code()).isNotBlank();
        assertThat(c.habits()).containsEntry("흥", CharacterContent.NO_OPENING);
        assertThat(c.rules()).contains("정답을 물으면 정답"); // PM: 답 대신 힌트만 → 정답은 정답, 힌트는 힌트
        assertThat(c.fallbackExpression("화남")).isEqualTo("화남"); // 8개 모두 에셋이 있어 목록 안 표정은 그대로
    }

    @Test
    void scriptedLinesRotateInOrder() {
        String session = "rotation-test";
        // 단계 1의 BREAK는 PM 대사 2줄: 순서대로 돈다. 단계 4는 단계 4 묶음
        String first =
                contents.next(1, "BREAK", null, 1, session, "민수").orElseThrow().content();
        String second =
                contents.next(1, "BREAK", null, 1, session, "민수").orElseThrow().content();
        String third =
                contents.next(1, "BREAK", null, 1, session, "민수").orElseThrow().content();
        assertThat(first).isNotEqualTo(second);
        assertThat(third).isEqualTo(first);
        assertThat(contents.next(1, "BREAK", null, 4, session, "민수")
                        .orElseThrow()
                        .content())
                .startsWith("쉬자.") // 단계 4 묶음 (두 줄 중 순서대로)
                .doesNotContain("체력도 실력");
        // 강도 칸(AWAY_2)이 있으면 그 칸, 없는 상황은 빈 값 (공통 고정 대사로)
        assertThat(contents.next(1, "AWAY_2", "AWAY", 1, session, "민수")
                        .orElseThrow()
                        .content())
                .isEqualTo("…진짜 간 거야? 금방 와.");
        assertThat(contents.next(1, "NO_SUCH", null, 1, session, "민수")).isEmpty();
        assertThat(contents.next(1, "GREETING", null, 1, session, "민수")
                        .orElseThrow()
                        .content())
                .startsWith("뭐야, 너."); // 단계 1은 이름을 부르지 않는다
    }

    @Test
    void momentAndScriptedLinesHaveThreePerStage() {
        CharacterContent c = contents.find(1).orElseThrow();
        // 순간 동작 · 5초 대체 · 완료 · 공부 포기는 단계마다 세 줄 (같은 대사가 연달아 나오지 않게, 승희 2026-11-05)
        for (String situation : List.of("YAWN", "WAVE", "HEAD_TURN", "REPLY_FALLBACK", "CLOSING", "GIVE_UP")) {
            for (int stage = 1; stage <= 4; stage++) {
                assertThat(c.linesFor(situation, stage))
                        .as(situation + " 단계 " + stage)
                        .hasSize(3);
            }
        }
        // 모든 대사의 표정은 확정 8개 안 (근황 TODAY는 표정 없음). 옛 '찡그림' 같은 어긋남을 잡는다
        assertThat(c.lines())
                .filteredOn(l -> !l.situation().equals("TODAY"))
                .allSatisfy(l -> assertThat(c.expressions())
                        .as(l.situation() + ": " + l.text())
                        .contains(l.expression()));
        // 5초 대체의 각 단계 첫 줄은 PM 문구, {name}은 닉네임으로
        String key = "fallback-test";
        assertThat(contents.next(1, "REPLY_FALLBACK", null, 4, key, "민수")
                        .orElseThrow()
                        .content())
                .isEqualTo("잠깐 생각중이야. 조금만 기다려.");
        assertThat(contents.next(1, "REPLY_FALLBACK", null, 4, key, "민수")
                        .orElseThrow()
                        .content())
                .isEqualTo("잠깐만, 민수. 대충 대답하기 싫어서 그래.");
    }

    @Test
    void labPreviewsMemoryDecisionWithoutSaving() throws Exception {
        // Fake 결정 모델은 첫 보기(무시)를 고른다. 개인정보 숫자는 묻지도 않는다
        String body = "{\"kind\":\"memory\",\"characterId\":1,\"message\":\"나 이번 달 토익 800 목표야\","
                + "\"config\":{\"facts\":[\"사용자는 밤에 공부가 잘 됨\"]}}";
        mvc.perform(post("/internal/llm-lab/api/playground")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].output.choice").value("NOOP"));
        mvc.perform(post("/internal/llm-lab/api/playground")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("토익 800 목표야", "번호 010-1234-5678")))
                .andExpect(jsonPath("$[0].output.choice").value(org.hamcrest.Matchers.startsWith("SKIPPED")));
    }

    @Test
    void labCanEditAndReseedWithoutCodeChanges() throws Exception {
        String json = mvc.perform(get(API + "/1/content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.speaker").value("말포이"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        CharacterContent c = LlmJson.read(json, CharacterContent.class);
        CharacterContent edited = new CharacterContent(
                c.characterId(),
                c.code(),
                c.speaker(),
                c.bible() + "\n- 실험: 모든 문장을 '흠'으로 끝낸다.",
                c.rules(),
                c.safety(),
                c.expressions(),
                c.expressionFallbacks(),
                c.stageTones(),
                c.studyStyle(),
                c.breakStyle(),
                c.world(),
                c.moves(),
                c.lines(),
                c.samples(),
                c.scores(),
                c.habits());
        mvc.perform(put(API + "/1/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LlmJson.write(edited)))
                .andExpect(status().isOk());
        assertThat(contents.find(1).orElseThrow().bible()).endsWith("'흠'으로 끝낸다.");

        mvc.perform(post(API + "/reseed/malfoy")).andExpect(status().isOk());
        assertThat(contents.find(1).orElseThrow().bible()).doesNotContain("'흠'으로");
    }

    @Test
    void firstSeedKeepsAnExistingWorldAndFindsTheCharacterByName() {
        CharacterContent git = CharacterContentSeeder.load("malfoy", 1);
        World w = git.world();
        World edited = new World(
                w.code(),
                w.name(),
                w.setting(),
                List.of(new Lore("lab_only", "실험 카드", List.of("실험"), "실험실에서 고친 지식")),
                List.of());
        Optional<Long> reseeded;
        try {
            repository.replace(withWorld(git, edited)); // 실험실에서 세계 지식을 고침
            repository.replace(git, false); // 같은 세계의 다른 캐릭터를 처음 불러올 때와 같다
            assertThat(repository.find(1).orElseThrow().world().lore())
                    .extracting(Lore::code)
                    .containsExactly("lab_only");
            assertThat(seeder.seed("malfoy", false)).isEmpty(); // 이미 있으면 파일을 읽지 않고 건너뜀
        } finally {
            reseeded = seeder.seed("malfoy", true); // 이름(말포이)으로 캐릭터 1을 찾아 git 값으로 되돌림 (다른 테스트를 위해 항상)
            contents.evict(1);
        }
        assertThat(reseeded).contains(1L);
        assertThat(repository.find(1).orElseThrow().world().lore())
                .extracting(Lore::code)
                .doesNotContain("lab_only"); // git 값(세계 지식 없음)으로 되돌아감
    }

    private static CharacterContent withWorld(CharacterContent c, World world) {
        return new CharacterContent(
                c.characterId(),
                c.code(),
                c.speaker(),
                c.bible(),
                c.rules(),
                c.safety(),
                c.expressions(),
                c.expressionFallbacks(),
                c.stageTones(),
                c.studyStyle(),
                c.breakStyle(),
                world,
                c.moves(),
                c.lines(),
                c.samples(),
                c.scores(),
                c.habits());
    }
}
