package com.capsule.dialogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.TestcontainersConfiguration;
import com.capsule.dialogue.client.LlmJson;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** LLM 실험실 API를 Fake 공급자로 끝까지 돌려 본다. 실험실은 로그인 없이 열린다 (local·test에서만 켜짐) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class LabTest {

    static final String API = "/internal/llm-lab/api";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void pageAndMetaOpenWithoutLogin() throws Exception {
        mvc.perform(get("/internal/llm-lab"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        // 토큰을 안 넣었으면 이 컴퓨터에서 온 요청만 (MockMvc 기본 주소는 127.0.0.1)
        mvc.perform(get(API + "/meta").with(r -> {
                    r.setRemoteAddr("192.168.0.23");
                    return r;
                }))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LAB_TOKEN_REQUIRED"));
        mvc.perform(get(API + "/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultProvider").value("fake"))
                .andExpect(jsonPath("$.characters[*].id").value(hasItem(1)))
                .andExpect(jsonPath("$.routes[?(@.purpose == 'CHAT')].models.anthropic")
                        .value(hasItem("claude-sonnet-5-5")))
                .andExpect(jsonPath("$.tokenRequired").value(false));
    }

    @Test
    void draftPromptCanBeTriedInLabThenActivated() throws Exception {
        mvc.perform(get(API + "/prompts").param("key", "chat.system"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].note").value(startsWith("시드")));

        long draft = id(call(
                post(API + "/prompts"),
                Map.of(
                        "key",
                        "chat.system",
                        "characterId",
                        1,
                        "body",
                        "너는 {{characterName}}. 실험 버전",
                        "note",
                        "실험",
                        "activate",
                        false)));
        long session = id(call(
                post(API + "/sessions"),
                Map.of(
                        "title",
                        "드래프트 시험",
                        "characterId",
                        1,
                        "pipeline",
                        "SIMPLE",
                        "config",
                        Map.of("nickname", "민수", "pinnedPrompts", Map.of("chat.system", draft)))));

        String turn = body(call(post(API + "/sessions/" + session + "/turns"), Map.of("text", "안녕"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("「안녕」… 그렇구나. 한 문제만 더 해보자."))
                .andExpect(jsonPath("$.emotion").value("NEUTRAL"))
                // 호감도: 결정(Fake = NEUTRAL) → 서비스였다면 0점. 답장과 결정은 동시에 나가서 기록 순서는 정해지지 않는다
                .andExpect(jsonPath("$.affinity").value(0))
                .andExpect(jsonPath("$.trace.signal").value("NEUTRAL"))
                .andExpect(jsonPath("$.overBudget").value(false))
                .andExpect(jsonPath("$.trace.prompts['chat.system'].id").value(draft))
                .andExpect(jsonPath("$.usages[*].purpose").value(containsInAnyOrder("CHAT", "DECIDE")))
                .andExpect(jsonPath("$.usages[?(@.purpose == 'CHAT')].request.system[0].text")
                        .value(contains("너는 채린. 실험 버전")))
                .andExpect(jsonPath("$.usages[?(@.purpose == 'CHAT')].request.system[0].cache")
                        .value(contains(true))));
        long turnId = ((Number) JsonPath.read(turn, "$.id")).longValue();
        call(patch(API + "/turns/" + turnId), Map.of("score", 5, "note", "좋음"))
                .andExpect(jsonPath("$.rating").value(5));

        // 두 번째 턴에는 앞 대화가 메시지로 들어간다
        call(post(API + "/sessions/" + session + "/turns"), Map.of("text", "수학 하는 중"))
                .andExpect(jsonPath("$.usages[?(@.purpose == 'CHAT')].request.messages.length()")
                        .value(contains(3)));

        // 켜면 이 캐릭터는 공통 시드보다 캐릭터 전용 버전을 쓴다
        call(post(API + "/prompts/" + draft + "/activate"), null)
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get(API + "/prompts/active").param("characterId", "1"))
                .andExpect(jsonPath("$['chat.system'].id").value(draft))
                .andExpect(jsonPath("$['chat.context'].characterId").value(org.hamcrest.Matchers.nullValue()));

        call(post(API + "/sessions/" + session + "/reset"), null)
                .andExpect(jsonPath("$.turnCount").value(0));
    }

    @Test
    void enginePackRunsEnginePipelineWithState() throws Exception {
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("characterId", 1);
        pack.put("characterYml", resource("characters/malfoy/character.yml"));
        pack.put("actor", resource("characters/malfoy/actor.md"));
        pack.put("director", resource("characters/malfoy/director.md"));
        pack.put("judge", resource("characters/malfoy/judge.md"));
        pack.put("activate", true);
        call(post(API + "/prompts/engine-pack"), pack)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));

        long session = id(call(post(API + "/sessions"), Map.of("characterId", 1, "pipeline", "ENGINE")));
        call(post(API + "/sessions/" + session + "/turns"), Map.of("text", "야 안녕"))
                .andExpect(jsonPath("$.reply").value("「야 안녕」… 그렇구나."))
                .andExpect(jsonPath("$.trace.pipeline").value("ENGINE"))
                .andExpect(jsonPath("$.usages[*].purpose").value(containsInAnyOrder("DIRECTOR", "ACTOR", "DECIDE")));
        call(post(API + "/sessions/" + session + "/turns"), Map.of("text", "공부 중이야"))
                .andExpect(jsonPath("$.error").doesNotExist());

        assertThat(jdbc.queryForObject(
                        "SELECT jsonb_array_length(state -> 'history') FROM llm_lab_sessions WHERE id = ?",
                        Integer.class,
                        session))
                .isEqualTo(4);

        // 엔진이 못 읽는 파일은 버전으로 올라가지 않는다
        pack.put("director", "수 목록 자리가 없는 감독 프롬프트");
        call(post(API + "/prompts/engine-pack"), pack)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ENGINE_PACK"));
    }

    @Test
    void playgroundRunsOneStepManyTimes() throws Exception {
        call(
                        post(API + "/playground"),
                        Map.of(
                                "kind",
                                "closing",
                                "characterId",
                                1,
                                "repeat",
                                3,
                                "config",
                                Map.of("nickname", "민수"),
                                "studySeconds",
                                3000))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].output.closingLine").value("(가짜 완료 대사) 오늘 공부 수고했어."))
                .andExpect(jsonPath("$[0].usages[0].purpose").value("CLOSING"))
                .andExpect(jsonPath("$[0].usages[0].request.system[0].text")
                        .value(org.hamcrest.Matchers.containsString("50분")));

        call(post(API + "/playground"), Map.of("kind", "reaction", "characterId", 1, "trigger", "AWAY", "intensity", 3))
                .andExpect(jsonPath("$[0].output.content").value(startsWith("(가짜 반응)")))
                .andExpect(jsonPath("$[0].usages[0].request.system[0].text")
                        .value(org.hamcrest.Matchers.containsString("5분째 자리에 없다")));

        mvc.perform(get(API + "/usages/summary").param("days", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].purpose").value(hasItem("CLOSING")));
    }

    @Test
    void evalRunFinishesWithJudgeAndHumanScores() throws Exception {
        long caseId = id(call(
                post(API + "/eval/cases"),
                Map.of(
                        "name",
                        "짧은 대화",
                        "characterId",
                        1,
                        "turns",
                        List.of("안녕", "오늘 수학 해"),
                        "context",
                        Map.of("nickname", "민수"),
                        "rubric",
                        "짧게 답한다")));
        long runId = id(call(
                post(API + "/eval/runs"),
                Map.of("label", "기본", "caseIds", List.of(caseId), "repeats", 2, "pipeline", "SIMPLE", "judge", true)));

        String run = null;
        for (int i = 0; i < 100; i++) {
            run = body(mvc.perform(get(API + "/eval/runs/" + runId)));
            if (!"RUNNING".equals(JsonPath.read(run, "$.status"))) break;
            Thread.sleep(100);
        }
        assertThat((String) JsonPath.read(run, "$.status")).isEqualTo("DONE");
        assertThat((Integer) JsonPath.read(run, "$.done")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(run, "$.errors")).isZero();
        assertThat((Double) JsonPath.read(run, "$.avgJudge")).isEqualTo(7.0);
        assertThat((String) JsonPath.read(run, "$.results[0].caseName")).isEqualTo("짧은 대화");
        assertThat((List<?>) JsonPath.read(run, "$.results[0].transcript")).hasSize(2);

        long resultId = ((Number) JsonPath.read(run, "$.results[0].id")).longValue();
        call(patch(API + "/eval/results/" + resultId), Map.of("score", 4))
                .andExpect(jsonPath("$.humanScore").value(4));
        mvc.perform(get(API + "/eval/runs/" + runId))
                .andExpect(jsonPath("$.avgHuman").value(4.0));
    }

    private ResultActions call(MockHttpServletRequestBuilder request, Object body) throws Exception {
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(LlmJson.write(body));
        return mvc.perform(request);
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(body(result.andExpect(status().isOk())), "$.id")).longValue();
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String resource(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
