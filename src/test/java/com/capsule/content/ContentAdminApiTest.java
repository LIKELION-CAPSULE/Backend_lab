package com.capsule.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
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
        var req = ("PUT".equals(method) ? put(path) : "PATCH".equals(method) ? patch(path) : post(path))
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

    // ---- 2026-11-25: Aliving2D 런타임 주소 · 로그인 화면 메이트 48 · 아바타 등록

    private static final String RUNTIME =
            "\"enginePath\": \"aliving2d/v4/engine.js\", \"playerPath\": \"aliving2d/v4/player.js\","
                    + " \"reactionsPath\": \"aliving2d/v4/reactions.json\",";

    private static String withFields(String json, String fields) {
        return "{" + fields + json.strip().substring(1);
    }

    private long idOf(ResultActions r, String path) throws Exception {
        return ((Number) JsonPath.read(r.andReturn().getResponse().getContentAsString(), path)).longValue();
    }

    private long mateWith(Long modelId, String name) throws Exception {
        String body = modelId == null
                ? "{\"name\": \"" + name + "\"}"
                : "{\"name\": \"" + name + "\", \"modelId\": " + modelId + "}";
        long id = idOf(
                send("POST", "/internal/content/mates", body, TOKEN).andExpect(status().isCreated()), "$.data.mateId");
        jdbc.update("UPDATE characters SET sort_order = 9000 + id WHERE id = ?", id); // 시드 메이트보다 뒤에
        return id;
    }

    private static String unique(String name) {
        return name + (System.nanoTime() % 1_000_000) + "/v1/";
    }

    @Test
    void loginMatesShareTheExactModelOfMateDetail() throws Exception {
        long modelId = idOf(
                send("POST", "/internal/content/models", withFields(model(unique("login")), RUNTIME), TOKEN)
                        .andExpect(status().isCreated()),
                "$.data.modelId");
        long mate = mateWith(modelId, "로그인 메이트");

        String detail = mvc.perform(get("/api/mates/" + mate).with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.model.engineUrl")
                        .value(org.hamcrest.Matchers.endsWith("/aliving2d/v4/engine.js")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        // 로그인 없이, 짧은 캐시, model은 31과 문자 하나까지 같은 값
        String login = mvc.perform(get("/api/login-mates"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=300")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("public")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<Map<String, Object>> found = JsonPath.read(login, "$.data.mates[?(@.id == " + mate + ")]");
        assertThat(found).hasSize(1);
        Map<String, Object> detailModel = JsonPath.read(detail, "$.data.model");
        assertThat(found.get(0).get("model")).isEqualTo(detailModel);
        assertThat(found.get(0).get("ageRating")).isEqualTo("ALL");

        // 준비 중 · 성인 · 비활성 메이트는 빠진다
        long soon = mateWith(null, "준비 로그인");
        long adult = mateWith(register(unique("adult")), "성인 메이트");
        long off = mateWith(register(unique("off")), "꺼진 메이트");
        try {
            jdbc.update("UPDATE characters SET age_rating = 'ADULT' WHERE id = ?", adult);
            jdbc.update("UPDATE characters SET is_active = false WHERE id = ?", off);
            String after = mvc.perform(get("/api/login-mates"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            List<Object> ids = JsonPath.read(after, "$.data.mates[*].id");
            assertThat(ids).contains((int) mate).doesNotContain((int) soon, (int) adult, (int) off);
        } finally {
            // 다른 테스트의 홈 목록에 안 섞이게
            jdbc.update("UPDATE characters SET is_active = false WHERE id IN (?, ?, ?, ?)", mate, soon, adult, off);
        }
    }

    @Test
    void runtimePathsOnlyForAliving2dAndCanBeSetLater() throws Exception {
        String prefix = unique("rt");
        send("POST", "/internal/content/models", withFields(model(prefix), "\"format\": \"CUBISM\", " + RUNTIME), TOKEN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CONTENT"));
        send(
                        "POST",
                        "/internal/content/models",
                        withFields(model(prefix), "\"playerPath\": \"aliving2d/v4/player.js\","),
                        TOKEN)
                .andExpect(status().isBadRequest()); // player만 있고 engine 없음
        long modelId = register(prefix); // 엔진 없이 등록된 모델 (운영 채린처럼)
        send(
                        "PUT",
                        "/internal/content/models/" + modelId + "/runtime",
                        "{\"enginePath\": \"aliving2d/v4/engine.js\"}",
                        TOKEN)
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT engine_path FROM live2d_models WHERE id = ?", String.class, modelId))
                .isEqualTo("aliving2d/v4/engine.js");
        send("PUT", "/internal/content/models/" + modelId + "/runtime", "{\"enginePath\": \"../x.js\"}", TOKEN)
                .andExpect(status().isBadRequest());
        send("PUT", "/internal/content/models/999999/runtime", "{}", TOKEN).andExpect(status().isNotFound());
        send("PUT", "/internal/content/models/" + modelId + "/runtime", "{}", null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mateImagesAreSetPartiallyAndShownAsUrls() throws Exception {
        String prefix = unique("img");
        long mate = mateWith(register(prefix), "이미지 메이트");
        String images = "/internal/content/mates/" + mate + "/images";
        try {
            send(
                            "PATCH",
                            images,
                            "{\"illustrationPath\": \"" + prefix + "full.png\", \"cardImagePath\": \"" + prefix
                                    + "card.webp\", \"voiceSamplePath\": \"" + prefix + "sample.mp3\"}",
                            TOKEN)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.illustrationPath").value(prefix + "full.png"));
            mvc.perform(get("/api/mates/" + mate).with(jwt().jwt(j -> j.subject("1"))))
                    .andExpect(jsonPath("$.data.illustrationUrl")
                            .value(org.hamcrest.Matchers.endsWith("/" + prefix + "full.png")))
                    .andExpect(jsonPath("$.data.voiceSampleUrl")
                            .value(org.hamcrest.Matchers.endsWith("/" + prefix + "sample.mp3")));
            mvc.perform(get("/api/login-mates"))
                    .andExpect(jsonPath("$.data.mates[?(@.id == " + mate + ")].cardImageUrl")
                            .value(org.hamcrest.Matchers.contains(
                                    org.hamcrest.Matchers.endsWith(prefix + "card.webp"))));

            // 보낸 칸만 바뀌고(null은 그대로), ""는 지운다
            send("PATCH", images, "{\"voiceSamplePath\": \"\"}", TOKEN)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.illustrationPath").value(prefix + "full.png"))
                    .andExpect(jsonPath("$.data.cardImagePath").value(prefix + "card.webp"))
                    .andExpect(jsonPath("$.data.voiceSamplePath").doesNotExist());

            // 경로 · 확장자 검사, 없는 메이트, 토큰
            send("PATCH", images, "{\"illustrationPath\": \"../full.png\"}", TOKEN)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CONTENT"));
            send("PATCH", images, "{\"illustrationPath\": \"/" + prefix + "full.png\"}", TOKEN)
                    .andExpect(status().isBadRequest());
            send("PATCH", images, "{\"illustrationPath\": \"https://evil.example/full.png\"}", TOKEN)
                    .andExpect(status().isBadRequest());
            send("PATCH", images, "{\"cardImagePath\": \"" + prefix + "card.gif\"}", TOKEN)
                    .andExpect(status().isBadRequest());
            send("PATCH", images, "{\"voiceSamplePath\": \"" + prefix + "sample.png\"}", TOKEN)
                    .andExpect(status().isBadRequest());
            send("PATCH", "/internal/content/mates/999999/images", "{}", TOKEN)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("MATE_NOT_FOUND"));
            send("PATCH", images, "{}", null).andExpect(status().isUnauthorized());
        } finally {
            jdbc.update("UPDATE characters SET is_active = false WHERE id = ?", mate); // 다른 테스트의 목록에 안 섞이게
        }
    }

    @Autowired
    com.capsule.content.api.VoiceQuery voices;

    @Test
    void mateVoiceAndItsSampleAreSetTogether() throws Exception {
        String prefix = unique("vo");
        long mate = mateWith(register(prefix), "목소리 메이트");
        String url = "/internal/content/mates/" + mate + "/voice";
        try {
            // 정하기 전: 칸이 비어 있음 (dialogue는 운영 설정 기본값)
            assertThat(voices.voice(mate))
                    .hasValueSatisfying(v -> assertThat(v.voice()).isNull());
            send(
                            "PUT",
                            url,
                            "{\"voice\": \"nova\", \"model\": \"gpt-4o-mini-tts\", \"instructions\": \"다정하게 말한다.\","
                                    + " \"samplePath\": \"" + prefix + "media/voice-1.mp3\"}",
                            TOKEN)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.voice").value("nova"));
            assertThat(voices.voice(mate))
                    .contains(
                            new com.capsule.content.api.ContentTypes.VoiceInfo("nova", "gpt-4o-mini-tts", "다정하게 말한다."));
            mvc.perform(get("/api/mates/" + mate).with(jwt().jwt(j -> j.subject("1"))))
                    .andExpect(jsonPath("$.data.voiceSampleUrl")
                            .value(org.hamcrest.Matchers.endsWith("/" + prefix + "media/voice-1.mp3")));

            // 통째로 바꿈: 보내지 않은 칸은 비워진다 (옛 샘플이 남지 않게)
            send("PUT", url, "{\"voice\": \"coral\"}", TOKEN).andExpect(status().isOk());
            assertThat(voices.voice(mate))
                    .contains(new com.capsule.content.api.ContentTypes.VoiceInfo("coral", null, null));
            assertThat(jdbc.queryForObject("SELECT voice_sample_url FROM characters WHERE id = ?", String.class, mate))
                    .isNull();

            send("PUT", url, "{\"voice\": \"Nova!\"}", TOKEN).andExpect(status().isBadRequest());
            send("PUT", url, "{\"model\": \"../x\"}", TOKEN).andExpect(status().isBadRequest());
            send("PUT", url, "{\"samplePath\": \"" + prefix + "a.png\"}", TOKEN).andExpect(status().isBadRequest());
            send("PUT", url, "{\"instructions\": \"" + "가".repeat(1001) + "\"}", TOKEN)
                    .andExpect(status().isBadRequest());
            send("PUT", "/internal/content/mates/999999/voice", "{}", TOKEN).andExpect(status().isNotFound());
            send("PUT", url, "{}", null).andExpect(status().isUnauthorized());
            assertThat(voices.voice(999999)).isEmpty();
        } finally {
            jdbc.update("UPDATE characters SET is_active = false WHERE id = ?", mate);
        }
    }

    @Test
    void aDuplicateMateIsSwitchedOffNotDeleted() throws Exception {
        long mate = mateWith(register(unique("dup")), "중복 메이트");
        mvc.perform(get("/api/login-mates"))
                .andExpect(jsonPath("$.data.mates[?(@.id == " + mate + ")]").isNotEmpty());
        send("PATCH", "/internal/content/mates/" + mate, "{\"active\": false}", TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
        // 홈 · 로그인 목록에서 빠지고 상세는 404. 행은 남는다
        mvc.perform(get("/api/login-mates"))
                .andExpect(jsonPath("$.data.mates[?(@.id == " + mate + ")]").isEmpty());
        mvc.perform(get("/api/mates/" + mate).with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM characters WHERE id = ?", Integer.class, mate))
                .isEqualTo(1);
        // 순서만 바꾸면 켜짐 상태는 그대로
        send("PATCH", "/internal/content/mates/" + mate, "{\"sortOrder\": 9500}", TOKEN)
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.sortOrder").value(9500));
        send("PATCH", "/internal/content/mates/999999", "{\"active\": false}", TOKEN)
                .andExpect(status().isNotFound());
        send("PATCH", "/internal/content/mates/" + mate, "{\"active\": true}", null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void avatarsAreRegisteredAndSwitchedOff() throws Exception {
        String prefix = unique("av");
        long modelId = register(prefix);
        long avatar = idOf(
                send(
                                "POST",
                                "/internal/content/avatars",
                                "{\"name\": \"히요리\", \"modelId\": " + modelId + ", \"thumbnailPath\": \"" + prefix
                                        + "thumb.png\", \"sortOrder\": 9100}",
                                TOKEN)
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.data.active").value(true)),
                "$.data.avatarId");
        mvc.perform(get("/api/avatars").with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(jsonPath("$.data.avatars[?(@.id == " + avatar + ")].name")
                        .value(org.hamcrest.Matchers.contains("히요리")));
        // 같은 모델을 또 쓰면 409, 썸네일 경로가 틀리면 400
        send(
                        "POST",
                        "/internal/content/avatars",
                        "{\"name\": \"둘째\", \"modelId\": " + modelId + ", \"thumbnailPath\": \"a.png\"}",
                        TOKEN)
                .andExpect(status().isConflict());
        long other = register(unique("av2"));
        send(
                        "POST",
                        "/internal/content/avatars",
                        "{\"name\": \"셋째\", \"modelId\": " + other + ", \"thumbnailPath\": \"../a.png\"}",
                        TOKEN)
                .andExpect(status().isBadRequest());
        // 끄면 목록에서 빠진다 (고른 사용자가 있어 지우지 않음)
        send("PATCH", "/internal/content/avatars/" + avatar, "{\"active\": false}", TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
        mvc.perform(get("/api/avatars").with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(jsonPath("$.data.avatars[?(@.id == " + avatar + ")]").isEmpty());
        send("PATCH", "/internal/content/avatars/999999", "{\"active\": false}", TOKEN)
                .andExpect(status().isNotFound());
    }
}
