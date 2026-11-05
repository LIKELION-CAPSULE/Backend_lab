package com.capsule.dialogue;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 실험실 '운영 설정' (ADR 0026): 저장하면 다음 요청부터 서비스 규칙에 들어가고, 위험한 칸 · 틀린 값은 저장하지 않는다 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SettingsApiTest {

    static final String BASE = "/internal/llm-lab/api/settings";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JsonMapper json;

    @AfterEach
    void reset() throws Exception {
        for (String s : List.of("relation", "dialogue", "llm")) save(s, "{}").andExpect(status().isOk());
    }

    ResultActions save(String section, String body) throws Exception {
        return mvc.perform(put(BASE + "/" + section)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    JsonNode field(String section, String path) throws Exception {
        String body = mvc.perform(get(BASE)).andReturn().getResponse().getContentAsString();
        for (JsonNode s : json.readTree(body))
            if (s.path("section").asString().equals(section))
                for (JsonNode f : s.path("fields"))
                    if (f.path("path").asString().equals(path)) return f;
        throw new AssertionError(section + "." + path + " 없음");
    }

    @Test
    void listsEditablePathsOnlyNoKeysOrAddresses() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].section", containsInAnyOrder("relation", "dialogue", "llm")))
                .andExpect(content().string(not(containsString("apiKey"))))
                .andExpect(content().string(not(containsString("baseUrl"))))
                .andExpect(content().string(not(containsString("logContent"))));
    }

    @Test
    void savedStagesApplyToTheNextRelationRead() throws Exception {
        long userId = jdbc.queryForObject(
                "INSERT INTO users (nickname, email, password) VALUES ('설정', ?, 'x') RETURNING id",
                Long.class,
                "settings-" + UUID.randomUUID() + "@test.invalid");
        long sessionId = jdbc.queryForObject(
                "INSERT INTO study_sessions (user_id, room_id, background_id) VALUES (?, 1, 1) RETURNING id",
                Long.class,
                userId);
        mvc.perform(put("/internal/llm-lab/api/relations")
                        .param("userId", String.valueOf(userId))
                        .param("characterId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"points\": 50, \"sessionId\": " + sessionId + "}"))
                .andExpect(jsonPath("$.relation.stage").value(2)); // yml: 0 / 30 / 70 / 90

        save("relation", "{\"stages\": [{\"min\": 0, \"name\": \"처음\"}, {\"min\": 60, \"name\": \"친구\"}]}")
                .andExpect(status().isOk());

        mvc.perform(get("/internal/llm-lab/api/relations")
                        .param("userId", String.valueOf(userId))
                        .param("characterId", "1"))
                .andExpect(jsonPath("$.relation.stage").value(1))
                .andExpect(jsonPath("$.relation.stageName").value("처음"));
        org.assertj.core.api.Assertions.assertThat(
                        field("relation", "stages").path("overridden").asBoolean())
                .isTrue();
    }

    @Test
    void savedRouteReachesTheLabMetaRightAway() throws Exception {
        ObjectNode routes = (ObjectNode) field("llm", "routes").path("value").deepCopy();
        ((ObjectNode) routes.path("CHAT")).put("maxTokens", 123);

        save("llm", json.createObjectNode().set("routes", routes).toString()).andExpect(status().isOk());

        mvc.perform(get("/internal/llm-lab/api/meta"))
                .andExpect(
                        jsonPath("$.routes[?(@.purpose == 'CHAT')].maxTokens").value(123));

        // 용도 공급자로 fake를 넣어 '실제 서버를 가짜로' 검사를 돌아가지 못한다
        ((ObjectNode) routes.path("CHAT")).put("provider", "fake");
        save("llm", json.createObjectNode().set("routes", routes).toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail.path").value("routes"));
    }

    @Test
    void rejectsLockedPathsBadValuesAndUnknownSections() throws Exception {
        for (String[] bad : new String[][] {
            {"llm", "{\"anthropic.apiKey\": \"x\"}", "anthropic.apiKey"},
            {"llm", "{\"anthropic.baseUrl\": \"https://evil.invalid\"}", "anthropic.baseUrl"},
            {"llm", "{\"logContent\": true}", "logContent"},
            {"llm", "{\"provider\": \"jev\"}", "provider"},
            {"llm", "{\"fallbackProvider\": \"fake\"}", "fallbackProvider"},
            {"relation", "{\"levelSeconds\": 0}", "levelSeconds"},
            {"relation", "{\"stages\": [{\"min\": 10, \"name\": \"x\"}]}", "stages"},
            {"dialogue", "{\"chatPipeline\": \"ENGINE\"}", "chatPipeline"},
            {"dialogue", "{\"llmBudget\": \"PT0S\"}", "llmBudget"},
            // 빈 칸(null)도 500이 아니라 400
            {"llm", "{\"provider\": null}", "provider"},
            {"dialogue", "{\"llmBudget\": null}", "llmBudget"},
            {"llm", "{\"openai.reasoningEffort\": \"hard\"}", "openai.reasoningEffort"},
            {"dialogue", "{\"inFlightWait\": \"PT2S\"}", "inFlightWait"}, // llm-budget(5초)보다 짧음
            {"dialogue", "{\"awayIntensityMax\": 5}", "awayIntensityMax"}, // 명세 19 요청 검사 계약: 닫힌 칸
        }) {
            save(bad[0], bad[1])
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_SETTING"))
                    .andExpect(jsonPath("$.detail.path").value(bad[2]));
        }
        save("nope", "{}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTING_NOT_FOUND"));

        mvc.perform(get(BASE))
                .andExpect(jsonPath("$[*].fields[*].overridden", everyItem(org.hamcrest.Matchers.is(false))));
    }
}
