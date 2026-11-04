package com.capsule.dialogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.TestcontainersConfiguration;
import com.capsule.content.api.CharacterQuery;
import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.dialogue.service.PromptService;
import com.capsule.relation.api.RelationCommands;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.api.RelationTypes.AffinityChange;
import com.capsule.relation.api.RelationTypes.RelationView;
import com.capsule.session.api.SessionClosingHandler;
import com.capsule.session.api.SessionQuery;
import com.capsule.session.api.SessionTypes.SessionClosing;
import com.capsule.session.api.SessionTypes.SessionInfo;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 메시지(20)를 대화 엔진 파이프라인으로: 엔진 상태가 턴마다 dialogue_states에 쌓이고 공부 종료 때 지워진다 */
@SpringBootTest(properties = "capsule.dialogue.chat-pipeline=ENGINE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DialogueEngineTest {

    static final long CHARACTER_ID = 1;

    @MockitoBean
    SessionQuery sessionQuery;

    @MockitoBean
    CharacterQuery characterQuery;

    @MockitoBean
    RelationQuery relationQuery;

    @MockitoBean
    RelationCommands relationCommands;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PromptService prompts;

    @Autowired
    SessionClosingHandler closingHandler;

    long userId;
    long sessionId;

    @BeforeEach
    void setUp() throws Exception {
        userId = jdbc.queryForObject(
                "INSERT INTO users (nickname, email, password) VALUES ('승희', ?, 'x') RETURNING id",
                Long.class,
                UUID.randomUUID() + "@engine.test");
        sessionId = jdbc.queryForObject(
                "INSERT INTO study_sessions (user_id, room_id, background_id) VALUES (?, 1, 1) RETURNING id",
                Long.class,
                userId);
        when(sessionQuery.find(sessionId))
                .thenReturn(Optional.of(new SessionInfo(sessionId, userId, 1, CHARACTER_ID, Instant.now(), null)));
        when(characterQuery.find(CHARACTER_ID))
                .thenReturn(
                        Optional.of(new CharacterInfo(CHARACTER_ID, 1, "채린", null, List.of("츤데레"), "짝꿍", true, null)));
        when(relationQuery.view(userId, CHARACTER_ID))
                .thenReturn(new RelationView(CHARACTER_ID, 1, "아는 사이", 80, 0, null, 1, "아는 사이", 0, 3600));
        when(relationCommands.applyChatScore(eq(userId), eq(CHARACTER_ID), eq(sessionId), any(), any()))
                .thenReturn(new AffinityChange(0, 80, 1, false, 1, "아는 사이"));
        prompts.importEnginePack(
                CHARACTER_ID,
                resource("character.yml"),
                resource("actor.md"),
                resource("director.md"),
                resource("judge.md"),
                "테스트 콘텐츠",
                true);
    }

    @Test
    void engineStateIsSavedPerTurnAndDroppedAtClose() throws Exception {
        send("안녕")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("「안녕」… 그렇구나."))
                .andExpect(jsonPath("$.fallback").value(false))
                .andExpect(jsonPath("$.affinity.delta").value(0)); // 신호는 SIMPLE과 같은 읽기 (Fake 결정 = NEUTRAL)
        assertThat(historySize()).isEqualTo(2);

        send("수학 숙제 중").andExpect(status().isOk());
        assertThat(historySize()).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM llm_usages WHERE session_id = ? AND purpose IN ('DIRECTOR', 'ACTOR')",
                        Integer.class,
                        sessionId))
                .isEqualTo(4);

        closingHandler.close(new SessionClosing(sessionId, userId, CHARACTER_ID, "채린", "NORMAL", 600));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM dialogue_states WHERE session_id = ?", Integer.class, sessionId))
                .isZero();
    }

    private Integer historySize() {
        return jdbc.queryForObject(
                "SELECT jsonb_array_length(state -> 'history') FROM dialogue_states WHERE session_id = ? AND character_id = ?",
                Integer.class,
                sessionId,
                CHARACTER_ID);
    }

    private ResultActions send(String content) throws Exception {
        return mvc.perform(post("/api/sessions/{sessionId}/messages", sessionId)
                .with(jwt().jwt(j -> j.subject(String.valueOf(userId))))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"characterId\":" + CHARACTER_ID
                        + ",\"content\":\"" + content + "\",\"inputType\":\"TEXT\"}"));
    }

    private static String resource(String file) throws Exception {
        return new ClassPathResource("characters/malfoy/" + file).getContentAsString(StandardCharsets.UTF_8);
    }
}
