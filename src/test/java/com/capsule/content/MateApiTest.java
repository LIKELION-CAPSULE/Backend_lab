package com.capsule.content;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** api.md 31 메이트 상세 · 9 model.format. 시드(db/seed/R__demo_content.sql) 기준 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class MateApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void mateDetail() throws Exception {
        mvc.perform(get("/api/mates/1").with(jwt().jwt(j -> j.subject("7"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("말포이"))
                .andExpect(jsonPath("$.data.ageRating").value("ALL"))
                .andExpect(jsonPath("$.data.hashtags[0]").value("츤데레"))
                .andExpect(jsonPath("$.data.oneLiner").value("지기 싫어서 일부러 내 옆자리에 앉는 슬리데린 라이벌"))
                .andExpect(jsonPath("$.data.world").doesNotExist())
                .andExpect(jsonPath("$.data.model.format").value("CUBISM"))
                .andExpect(jsonPath("$.data.model.modelJsonUrl")
                        .value("https://cdn.capsule.invalid/chaerin/v3/chaerin.model3.json"))
                .andExpect(jsonPath("$.data.comingSoon").value(false))
                .andExpect(jsonPath("$.data.hasConversation").value(false)); // 대화방 없음 (6단계)
    }

    @Test
    void imagePathsGetCdnPrefix() throws Exception {
        long id = otherMate("images/test/full.png", "https://other.cdn/sample.mp3");
        mvc.perform(get("/api/mates/" + id).with(jwt().jwt(j -> j.subject("7"))))
                .andExpect(jsonPath("$.data.illustrationUrl").value("https://cdn.capsule.invalid/images/test/full.png"))
                .andExpect(jsonPath("$.data.voiceSampleUrl").value("https://other.cdn/sample.mp3"))
                .andExpect(jsonPath("$.data.comingSoon").value(true));
        jdbc.update("UPDATE characters SET is_active = false WHERE id = ?", id);
        mvc.perform(get("/api/mates/" + id).with(jwt().jwt(j -> j.subject("7"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATE_NOT_FOUND"));
    }

    @Test
    void missingMateIs404AndNeedsToken() throws Exception {
        mvc.perform(get("/api/mates/999999").with(jwt().jwt(j -> j.subject("7"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATE_NOT_FOUND"));
        mvc.perform(get("/api/mates/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void avatarModelHasFormat() throws Exception {
        mvc.perform(get("/api/avatars").with(jwt().jwt(j -> j.subject("7"))))
                .andExpect(jsonPath("$.data.avatars[0].model.format").value("CUBISM"));
    }

    /** 준비 중 메이트 하나 (characters.model_id가 UNIQUE라 모델도 새로) */
    private long otherMate(String illustration, String voice) {
        long modelId = jdbc.queryForObject(
                "INSERT INTO live2d_models (storage_prefix, model_json_path) VALUES ('test/', 'm.model3.json')"
                        + " RETURNING id",
                Long.class);
        return jdbc.queryForObject(
                "INSERT INTO characters (model_id, name, illustration_url, voice_sample_url, coming_soon)"
                        + " VALUES (?, '준비 중', ?, ?, true) RETURNING id",
                Long.class,
                modelId,
                illustration,
                voice);
    }
}
