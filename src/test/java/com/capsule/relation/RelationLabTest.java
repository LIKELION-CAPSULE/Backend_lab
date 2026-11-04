package com.capsule.relation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.TestcontainersConfiguration;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.api.RelationTypes.StudySettlement;
import java.util.UUID;
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

/** 흐름 테스트의 호감도 맞추기 (local 실험실 전용 내부 API) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RelationLabTest {

    static final long CHARACTER = 1;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RelationQuery query;

    @Test
    void adjustKeepsLedgerAndReportConsistent() throws Exception {
        long userId = jdbc.queryForObject(
                "INSERT INTO users (nickname, email, password) VALUES ('흐름', ?, 'x') RETURNING id",
                Long.class,
                "flow-" + UUID.randomUUID() + "@test.invalid");
        long sessionId = jdbc.queryForObject(
                "INSERT INTO study_sessions (user_id, room_id, background_id) VALUES (?, 1, 1) RETURNING id",
                Long.class,
                userId);

        adjust(userId, "{\"points\": 80, \"sessionId\": " + sessionId + "}")
                .andExpect(jsonPath("$.relation.points").value(80))
                .andExpect(jsonPath("$.relation.stage").value(3))
                .andExpect(jsonPath("$.logs[0].reason").value("ADJUST"))
                .andExpect(jsonPath("$.logs[0].delta").value(30));
        // 0~100 밖은 잘리고, 잘린 만큼만 기록된다
        adjust(userId, "{\"points\": 150, \"sessionId\": " + sessionId + "}")
                .andExpect(jsonPath("$.relation.points").value(100))
                .andExpect(jsonPath("$.logs[0].delta").value(20));
        // 점수는 세션 기록으로만 (세션 없는 기록은 리포트 계산에서 빠질 수 있음). 시간만 맞출 때는 세션이 없어도 된다
        mvc.perform(put("/internal/llm-lab/api/relations")
                        .param("userId", String.valueOf(userId))
                        .param("characterId", String.valueOf(CHARACTER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"points\": 10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));
        adjust(userId, "{\"presentMinutes\": 125}")
                .andExpect(jsonPath("$.relation.points").value(100))
                .andExpect(jsonPath("$.relation.level").value(3))
                .andExpect(jsonPath("$.relation.totalPresentSeconds").value(7500))
                .andExpect(jsonPath("$.relation.totalStudySeconds").value(7500));

        // 50 + 기록의 합 = 지금 점수 → 리포트도 같은 값을 계산한다
        int logSum = jdbc.queryForObject(
                "SELECT sum(delta) FROM relation_logs l JOIN user_character_relations r ON r.id = l.relation_id"
                        + " WHERE r.user_id = ?",
                Integer.class,
                userId);
        assertThat(50 + logSum).isEqualTo(100);
        StudySettlement report = query.settlementOf(userId, CHARACTER, sessionId);
        assertThat(report.pointsBefore()).isEqualTo(50);
        assertThat(report.pointsAfter()).isEqualTo(100);

        mvc.perform(get("/internal/llm-lab/api/relations")
                        .param("userId", String.valueOf(userId))
                        .param("characterId", String.valueOf(CHARACTER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logs.length()").value(2));
    }

    @Test
    void flowPageAndAdjustOnlyFromThisComputer() throws Exception {
        mvc.perform(get("/internal/llm-lab/flow"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        // 실험실 보안 체인이 이 경로도 맡는다: 토큰이 없으면 이 컴퓨터(loopback)에서 온 요청만
        mvc.perform(put("/internal/llm-lab/api/relations")
                        .param("userId", "1")
                        .param("characterId", String.valueOf(CHARACTER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"points\": 100}")
                        .with(r -> {
                            r.setRemoteAddr("192.168.0.23");
                            return r;
                        }))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions adjust(long userId, String body) throws Exception {
        return mvc.perform(put("/internal/llm-lab/api/relations")
                        .param("userId", String.valueOf(userId))
                        .param("characterId", String.valueOf(CHARACTER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
