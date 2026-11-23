package com.capsule.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
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

/** 콘텐츠 관리용 API (ADR 0033): 토큰, 모델 등록, 메이트 만들기 · 모델 바꾸기 */
@SpringBootTest(properties = "capsule.content.admin.token=content-secret")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ContentAdminApiTest {

    static final String TOKEN = "Bearer content-secret";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions send(String method, String path, String body, String auth) throws Exception {
        var req = ("PUT".equals(method) ? put(path) : post(path))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (auth != null) req.header("Authorization", auth);
        return mvc.perform(req);
    }

    private static String model(String prefix) {
        return """
                {"storagePrefix": "%s", "modelJsonPath": "model.json", "scale": 1.0,
                 "expressions": [{"name": "sulky", "label": "삐짐", "file": "expressions/sulky.exp3.json"},
                                 {"name": "laugh", "label": "웃음", "file": "expressions/laugh.exp3.json"}],
                 "motions": [{"group": "Idle", "name": "idle", "label": "대기", "file": "motions/idle.motion3.json"}],
                 "reactions": [{"trigger": "AWAY", "intensity": 2, "expression": "sulky"},
                               {"trigger": "GREETING", "expression": "laugh"},
                               {"trigger": "삐짐", "expression": "sulky"}]}""".formatted(prefix);
    }

    private long register(String prefix) throws Exception {
        String res = send("POST", "/internal/content/models", model(prefix), TOKEN)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(res, "$.data.modelId")).longValue();
    }

    @Test
    void listsMatesWithTheirModels() throws Exception {
        mvc.perform(get("/internal/content/mates").header("Authorization", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("말포이"))
                .andExpect(jsonPath("$.data[0].modelPath").value("chaerin/v3/"))
                .andExpect(jsonPath("$.data[0].modelFormat").value("CUBISM"));
        mvc.perform(get("/internal/content/mates")).andExpect(status().isUnauthorized());
    }

    @Test
    void needsTheToken() throws Exception {
        send("POST", "/internal/content/models", model("t1/v1/"), null).andExpect(status().isUnauthorized());
        send("POST", "/internal/content/models", model("t1/v1/"), "Bearer wrong")
                .andExpect(status().isUnauthorized());
        send("POST", "/internal/content/models", model("t1/v1/"), "content-secret")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registersAModelWithItsRows() throws Exception {
        long id = register("admin" + System.nanoTime() % 100000 + "/v1/");
        assertThat(jdbc.queryForObject("SELECT format FROM live2d_models WHERE id = ?", String.class, id))
                .isEqualTo("ALIVING2D_V2");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM live2d_expressions WHERE model_id = ?", Long.class, id))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "SELECT label FROM live2d_expressions WHERE model_id = ? AND name = 'sulky'", String.class, id))
                .isEqualTo("삐짐");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM live2d_motions WHERE model_id = ?", Long.class, id))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                        SELECT e.name FROM live2d_reaction_maps r JOIN live2d_expressions e ON e.id = r.expression_id
                        WHERE r.model_id = ? AND r.trigger_type = 'AWAY' AND r.intensity = 2""", String.class, id)).isEqualTo("sulky");
    }

    @Test
    void refusesBadValues() throws Exception {
        String prefix = "bad" + System.nanoTime() % 100000 + "/v1/";
        send("POST", "/internal/content/models", model("한글/v1/"), TOKEN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CONTENT"));
        send(
                        "POST",
                        "/internal/content/models",
                        model(prefix).replace("\"expression\": \"laugh\"", "\"expression\": \"nope\""),
                        TOKEN)
                .andExpect(status().isBadRequest());
        send("POST", "/internal/content/models", model(prefix).replace("expressions/sulky", "../sulky"), TOKEN)
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM live2d_models WHERE storage_prefix = ?", Long.class, prefix))
                .isZero(); // 검사에 걸리면 아무것도 넣지 않음
        register(prefix);
        send("POST", "/internal/content/models", model(prefix), TOKEN) // 같은 경로는 다시 못 넣음 (새 버전 폴더로)
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsAMateAndSwapsItsModel() throws Exception {
        String name = "m" + System.nanoTime() % 100000;
        long v1 = register(name + "/v1/"), v2 = register(name + "/v2/");
        String res = send(
                        "POST",
                        "/internal/content/mates",
                        "{\"name\": \"새 메이트\", \"oneLiner\": \"한 줄\", \"hashtags\": [\"다정\"], \"modelId\": " + v1 + "}",
                        TOKEN)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.comingSoon").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long mate = ((Number) JsonPath.read(res, "$.data.mateId")).longValue();
        mvc.perform(get("/api/mates/" + mate).with(jwt().jwt(j -> j.subject("7"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hashtags[0]").value("다정"))
                .andExpect(jsonPath("$.data.model.format").value("ALIVING2D_V2"))
                .andExpect(jsonPath("$.data.model.modelJsonUrl")
                        .value("https://cdn.capsule.invalid/" + name + "/v1/model.json"));
        send("PUT", "/internal/content/mates/" + mate + "/model", "{\"modelId\": " + v2 + "}", TOKEN)
                .andExpect(status().isOk());
        mvc.perform(get("/api/mates/" + mate).with(jwt().jwt(j -> j.subject("7"))))
                .andExpect(jsonPath("$.data.model.modelJsonUrl")
                        .value("https://cdn.capsule.invalid/" + name + "/v2/model.json"));
        // 다른 메이트가 쓰는 모델 · 아바타 모델(시드 2)은 못 붙임
        send("POST", "/internal/content/mates", "{\"name\": \"둘째\", \"modelId\": " + v2 + "}", TOKEN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MODEL_IN_USE"));
        send("PUT", "/internal/content/mates/" + mate + "/model", "{\"modelId\": 2}", TOKEN)
                .andExpect(status().isConflict());
        send("PUT", "/internal/content/mates/999999/model", "{\"modelId\": " + v1 + "}", TOKEN)
                .andExpect(status().isNotFound());
        // 모델 없이 만들면 준비 중 메이트
        send("POST", "/internal/content/mates", "{\"name\": \"준비\"}", TOKEN)
                .andExpect(jsonPath("$.data.comingSoon").value(true));
    }
}
