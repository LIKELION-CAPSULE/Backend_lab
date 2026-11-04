package com.capsule.dialogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.TestcontainersConfiguration;
import com.capsule.dialogue.client.LlmJson;
import com.capsule.dialogue.dto.CharacterContent;
import com.capsule.dialogue.service.CharacterContents;
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

    @Test
    void malfoyIsSeededIntoTheDatabase() {
        CharacterContent c = contents.find(1).orElseThrow();
        assertThat(c.speaker()).isEqualTo("말포이");
        assertThat(c.safety()).isEqualTo(CharacterContent.MINOR);
        assertThat(c.expressions()).containsExactly("웃음", "찡그림", "화남", "삐짐", "수줍음", "집중");
        assertThat(c.world().lore()).extracting(CharacterContent.Lore::code).contains("pince", "library");
        assertThat(c.moves())
                .extracting(CharacterContent.Move::code)
                .contains("yes_and")
                .doesNotContain("callback");
        assertThat(c.habits()).containsEntry("흥", CharacterContent.NO_OPENING);
        assertThat(c.rules()).contains("정답을 물으면 정답"); // PM: 답 대신 힌트만 → 정답은 정답, 힌트는 힌트
        assertThat(c.fallbackExpression("화남")).isEqualTo("찡그림");
    }

    @Test
    void scriptedLinesRotateInOrder() {
        String session = "rotation-test";
        String first =
                contents.next(1, "BREAK", null, 2, session, "민수").orElseThrow().content();
        String second =
                contents.next(1, "BREAK", null, 2, session, "민수").orElseThrow().content();
        String third =
                contents.next(1, "BREAK", null, 2, session, "민수").orElseThrow().content();
        assertThat(first).isNotEqualTo(second);
        assertThat(third).isEqualTo(first);
        // 강도 칸(AWAY_2)이 있으면 그 칸, 없는 상황은 빈 값 (공통 고정 대사로)
        assertThat(contents.next(1, "AWAY_2", "AWAY", 1, session, "민수")
                        .orElseThrow()
                        .content())
                .isEqualTo("…진짜 간 거야? 금방 와.");
        assertThat(contents.next(1, "NO_SUCH", null, 1, session, "민수")).isEmpty();
        assertThat(contents.next(1, "GREETING", null, 1, session, "민수")
                        .orElseThrow()
                        .content())
                .startsWith("뭐야, 민수.");
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
                c.world(),
                c.moves(),
                c.lines(),
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
}
