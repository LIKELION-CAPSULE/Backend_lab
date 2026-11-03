package com.capsule.session.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.TestcontainersConfiguration;
import com.capsule.content.api.AvatarQuery;
import com.capsule.content.api.CharacterQuery;
import com.capsule.content.api.ContentTypes.AvatarInfo;
import com.capsule.content.api.ContentTypes.BackgroundInfo;
import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.content.api.ContentTypes.ModelInfo;
import com.capsule.content.api.ContentTypes.RoomInfo;
import com.capsule.content.api.RoomQuery;
import com.capsule.relation.api.RelationCommands;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.api.RelationTypes.RelationView;
import com.capsule.relation.api.RelationTypes.SessionEventRef;
import com.capsule.relation.api.RelationTypes.StudySettlement;
import com.capsule.session.api.SessionClosingHandler;
import com.capsule.session.api.SessionQuery;
import com.capsule.session.api.SessionTypes.SessionClosing;
import com.capsule.session.api.SessionTypes.SessionEventInfo;
import com.capsule.session.api.SessionTypes.SessionInfo;
import com.capsule.session.internal.service.SessionService;
import com.capsule.user.api.ActiveSessionLookup;
import com.capsule.user.api.UserQuery;
import com.capsule.user.api.UserTypes.ActiveSessionSummary;
import com.capsule.user.api.UserTypes.UserProfile;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** session API 통합 테스트. 다른 모듈 api는 전부 목, 값은 테스트 시드(룸 1 · 채린 · 배경 1 · 아바타 1)와 맞춘다 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SessionApiTest {

    static final ModelInfo CHAERIN_MODEL =
            new ModelInfo("https://cdn.capsule.invalid/live2d/chaerin/v1/chaerin.model3.json", 1.0, 0, 12);
    static final CharacterInfo CHAERIN = new CharacterInfo(
            1,
            1,
            "채린",
            "https://cdn.capsule.invalid/chaerin.png",
            List.of("츤데레", "같은 반 친구"),
            "차갑게 굴지만 나한테만 다정한 짝꿍",
            true,
            CHAERIN_MODEL);
    static final RoomInfo ROOM = new RoomInfo(
            1,
            "쌀쌀맞은 갸루짝꿍이 나에게만 친절하다!",
            "차갑게 굴지만 나한테만 다정한 짝꿍과 같이 공부해요.",
            "https://cdn.capsule.invalid/room1.png",
            true,
            List.of(CHAERIN));
    static final BackgroundInfo BACKGROUND = new BackgroundInfo(1, 1, "교실", "https://cdn.capsule.invalid/room1-bg.png");
    static final AvatarInfo AVATAR = new AvatarInfo(
            1,
            "기본 아바타",
            "https://cdn.capsule.invalid/avatar1.png",
            true,
            new ModelInfo("https://cdn.capsule.invalid/live2d/avatar1/v1/avatar1.model3.json", 1.0, 0, 0));
    static final StudySettlement SETTLEMENT = new StudySettlement(1, 1, "아는 사이", 0, 55, 100, false);
    static final String CLOSING_LINE = "오늘도 같이 공부해서 좋았어.";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SessionService service;

    @Autowired
    SessionQuery sessionQuery;

    @Autowired
    ActiveSessionLookup activeSessionLookup;

    @MockitoBean
    UserQuery userQuery;

    @MockitoBean
    AvatarQuery avatarQuery;

    @MockitoBean
    RoomQuery roomQuery;

    @MockitoBean
    CharacterQuery characterQuery;

    @MockitoBean
    RelationQuery relationQuery;

    @MockitoBean
    RelationCommands relationCommands;

    @MockitoBean
    SessionClosingHandler closingHandler;

    long userId;

    @BeforeEach
    void setUp() {
        userId = newUser();
        given(userQuery.find(anyLong()))
                .willAnswer(inv -> Optional.of(
                        new UserProfile(inv.<Long>getArgument(0), "승희", null, 1L, AVATAR.thumbnailUrl(), false)));
        given(avatarQuery.find(1L)).willReturn(Optional.of(AVATAR));
        given(roomQuery.find(1L)).willReturn(Optional.of(ROOM));
        given(roomQuery.activeRooms()).willReturn(List.of(ROOM));
        given(roomQuery.defaultBackground(1L)).willReturn(Optional.of(BACKGROUND));
        given(roomQuery.background(1L)).willReturn(Optional.of(BACKGROUND));
        given(characterQuery.find(1L)).willReturn(Optional.of(CHAERIN));
        given(relationQuery.view(anyLong(), eq(1L))).willReturn(new RelationView(1, 1, "아는 사이", 0, 0, 100));
        given(relationQuery.settlementOf(anyLong(), anyLong(), anyLong())).willReturn(SETTLEMENT);
        given(relationCommands.settleStudyTime(anyLong(), anyLong(), anyLong(), anyInt()))
                .willReturn(SETTLEMENT);
        given(closingHandler.close(any())).willReturn(CLOSING_LINE);
    }

    // ---- 16 공부 시작

    @Test
    void startCreatesSessionWithScene() throws Exception {
        startRequest(userId, 50)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").isNumber())
                .andExpect(jsonPath("$.startedAt").isString())
                .andExpect(jsonPath("$.targetMinutes").value(50))
                .andExpect(jsonPath("$.room.name").value(ROOM.name()))
                .andExpect(jsonPath("$.room.backgroundImageUrl").value(BACKGROUND.imageUrl()))
                .andExpect(jsonPath("$.character.modelJsonUrl").value(CHAERIN_MODEL.modelJsonUrl()))
                .andExpect(jsonPath("$.character.offsetY").value(12))
                .andExpect(jsonPath("$.avatar.profileImageUrl").value(AVATAR.thumbnailUrl()))
                .andExpect(jsonPath("$.me.nickname").value("승희"))
                .andExpect(jsonPath("$.me.profileImageUrl").value(AVATAR.thumbnailUrl()))
                .andExpect(jsonPath("$.serverTime").isString());

        verify(relationCommands).meet(userId, 1L);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM study_sessions s JOIN session_characters c ON c.session_id = s.id"
                                + " WHERE s.user_id = ? AND c.character_id = 1 AND s.background_id = 1"
                                + " AND s.last_heartbeat_at IS NOT NULL",
                        Integer.class,
                        userId))
                .isEqualTo(1);
    }

    @Test
    void startRejectsInvalidRequests() throws Exception {
        expectError(startRequest(userId, 52), 400, "INVALID_TARGET_MINUTES");
        expectError(startRequest(userId, 185), 400, "INVALID_TARGET_MINUTES");
        expectError(
                startRequest(userId, "{\"roomId\":99,\"characterId\":1,\"targetMinutes\":50}"), 404, "ROOM_NOT_FOUND");
        expectError(
                startRequest(userId, "{\"roomId\":1,\"characterId\":2,\"targetMinutes\":50}"),
                400,
                "CHARACTER_NOT_IN_ROOM");

        given(userQuery.find(userId)).willReturn(Optional.of(new UserProfile(userId, "승희", null, null, null, true)));
        expectError(startRequest(userId, 50), 403, "ONBOARDING_REQUIRED");
    }

    @Test
    void startWhileInProgressPointsToExistingSession() throws Exception {
        long sessionId = start(userId);

        expectError(startRequest(userId, 50), 409, "SESSION_ALREADY_IN_PROGRESS")
                .andExpect(jsonPath("$.detail.sessionId").value(sessionId));
    }

    // ---- 18 상태 변화 + SessionQuery.findEvent

    @Test
    void eventsAreSavedOnceAndIgnoredDuringBreak() throws Exception {
        long sessionId = start(userId);
        Instant t0 = startedAt(sessionId);

        long focus = event(sessionId, "FOCUS", t0, 201);
        assertThat(event(sessionId, "FOCUS", t0, 200)).isEqualTo(focus); // 재전송
        verify(relationCommands).applySessionEvent(new SessionEventRef(userId, 1, sessionId, focus, "FOCUS", true));

        event(sessionId, "BREAK", t0.plusSeconds(60), 201);
        postEvent(userId, sessionId, "AWAY", t0.plusSeconds(70))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(nullValue()));
        long focusAfterBreak = event(sessionId, "FOCUS", t0.plusSeconds(120), 201);
        verify(relationCommands, times(3)).applySessionEvent(any()); // 중복·휴식 중 무시는 반영 안 함

        assertThat(sessionQuery.findEvent(sessionId, focus))
                .get()
                .extracting(SessionEventInfo::firstFocusOfSession, SessionEventInfo::afterBreak)
                .containsExactly(true, false);
        assertThat(sessionQuery.findEvent(sessionId, focusAfterBreak))
                .get()
                .extracting(SessionEventInfo::firstFocusOfSession, SessionEventInfo::afterBreak)
                .containsExactly(false, true);
        assertThat(sessionQuery.findEvent(sessionId + 1000, focus)).isEmpty();

        expectError(postEvent(userId, sessionId, "SLEEP", t0), 400, "INVALID_EVENT");
        // 프론트 시계가 늦어 시작 전 시각이 와도 시작 시각으로 맞춰진다 (여기선 t0의 FOCUS와 같아져 재전송으로 처리됨)
        assertThat(event(sessionId, "FOCUS", t0.minusSeconds(60), 200)).isEqualTo(focus);
        expectError(postEvent(newUser(), sessionId, "FOCUS", t0), 404, "SESSION_NOT_FOUND");
    }

    // ---- 17 이어하기 · 21 heartbeat

    @Test
    void resumeTurnsGapIntoBreakWithoutTouchingRelation() throws Exception {
        long sessionId = start(userId);
        event(sessionId, "FOCUS", startedAt(sessionId), 201);
        // 30분 전에 시작해 10분 공부하고 끊긴 것으로 옮긴다
        jdbc.update(
                "UPDATE study_sessions SET started_at = started_at - interval '30 minutes',"
                        + " last_heartbeat_at = started_at - interval '20 minutes' WHERE id = ?",
                sessionId);
        jdbc.update(
                "UPDATE session_events SET occurred_at = occurred_at - interval '30 minutes' WHERE session_id = ?",
                sessionId);
        clearInvocations(relationCommands);

        mvc.perform(post("/api/sessions/{id}/resume", sessionId).with(login(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.startedAt").doesNotExist())
                .andExpect(jsonPath("$.character.name").value("채린"))
                .andExpect(jsonPath("$.avatar.id").value(1))
                .andExpect(jsonPath("$.studySeconds").value(600))
                .andExpect(jsonPath("$.breakSeconds").value(greaterThanOrEqualTo(1200)));

        assertThat(jdbc.queryForList(
                        "SELECT type FROM session_events WHERE session_id = ? ORDER BY occurred_at, id",
                        String.class,
                        sessionId))
                .containsExactly("FOCUS", "BREAK", "FOCUS");
        verifyNoInteractions(relationCommands);
    }

    @Test
    void heartbeatRefreshesLastHeartbeat() throws Exception {
        long sessionId = start(userId);
        jdbc.update("UPDATE study_sessions SET last_heartbeat_at = NULL WHERE id = ?", sessionId);

        mvc.perform(post("/api/sessions/{id}/heartbeat", sessionId).with(login(userId)))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
                        "SELECT last_heartbeat_at IS NOT NULL FROM study_sessions WHERE id = ?",
                        Boolean.class,
                        sessionId))
                .isTrue();
    }

    // ---- 22 공부 종료 · 23 리포트

    @Test
    void endValidatesSettlesOnceAndReportRepeatsResult() throws Exception {
        long sessionId = start(userId);
        Instant t0 = startedAt(sessionId);
        event(sessionId, "FOCUS", t0, 201);
        event(sessionId, "AWAY", t0.plusSeconds(600), 201);
        event(sessionId, "RETURN", t0.plusSeconds(1020), 201);
        jdbc.update("UPDATE study_sessions SET started_at = started_at - interval '1 hour' WHERE id = ?", sessionId);
        jdbc.update(
                "UPDATE session_events SET occurred_at = occurred_at - interval '1 hour' WHERE session_id = ?",
                sessionId);

        expectError(end(sessionId, 3700, 100), 400, "INVALID_STUDY_TIME"); // 경과 시간(1시간) 초과
        expectError(end(sessionId, 3300, 3400), 400, "INVALID_STUDY_TIME");
        expectError(end(sessionId, 3300, -1), 400, "INVALID_STUDY_TIME");

        expectReport(end(sessionId, 3300, 2880).andExpect(status().isOk()), sessionId);
        expectReport(end(sessionId, 10, 10).andExpect(status().isOk()), sessionId); // 다시 불러도 같은 결과
        expectReport(
                mvc.perform(get("/api/sessions/{id}/report", sessionId).with(login(userId)))
                        .andExpect(status().isOk()),
                sessionId);

        verify(relationCommands, times(1)).settleStudyTime(anyLong(), anyLong(), anyLong(), anyInt());
        verify(relationCommands).settleStudyTime(userId, 1L, sessionId, 3300);
        verify(closingHandler, times(1)).close(any());
        verify(closingHandler).close(new SessionClosing(sessionId, userId, 1L, "채린", "NORMAL", 3300));
        assertThat(jdbc.queryForMap(
                        "SELECT end_type, focus_seconds, distract_seconds, return_count, closing_line"
                                + " FROM study_sessions WHERE id = ?",
                        sessionId))
                .containsEntry("end_type", "NORMAL")
                .containsEntry("focus_seconds", 2880)
                .containsEntry("distract_seconds", 420)
                .containsEntry("return_count", 1)
                .containsEntry("closing_line", CLOSING_LINE);

        expectError(
                mvc.perform(post("/api/sessions/{id}/heartbeat", sessionId).with(login(userId))), 409, "SESSION_ENDED");
        expectError(
                mvc.perform(post("/api/sessions/{id}/resume", sessionId).with(login(userId))), 409, "SESSION_ENDED");
        expectError(postEvent(userId, sessionId, "FOCUS", Instant.now()), 409, "SESSION_ENDED");
    }

    @Test
    void reportOfInProgressSessionIsConflict() throws Exception {
        long sessionId = start(userId);

        expectError(
                mvc.perform(get("/api/sessions/{id}/report", sessionId).with(login(userId))),
                409,
                "SESSION_IN_PROGRESS");
        expectError(
                mvc.perform(get("/api/sessions/{id}/report", sessionId).with(login(newUser()))),
                404,
                "SESSION_NOT_FOUND");
    }

    // ---- 11 홈 · 12 룸 상세 · ActiveSessionLookup

    @Test
    void homeShowsRoomsAndActiveSession() throws Exception {
        mvc.perform(get("/api/home").with(login(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSession").value(nullValue()))
                .andExpect(jsonPath("$.rooms[0].thumbnailUrl").value(ROOM.thumbnailUrl()))
                .andExpect(jsonPath("$.rooms[0].characters[0].profileImageUrl").value(CHAERIN.profileImageUrl()));
        assertThat(activeSessionLookup.find(userId)).isEmpty();

        long sessionId = start(userId);

        mvc.perform(get("/api/home").with(login(userId)))
                .andExpect(jsonPath("$.activeSession.sessionId").value(sessionId))
                .andExpect(jsonPath("$.activeSession.roomName").value(ROOM.name()))
                .andExpect(jsonPath("$.activeSession.characterName").value("채린"));
        assertThat(activeSessionLookup.find(userId))
                .get()
                .extracting(ActiveSessionSummary::sessionId, ActiveSessionSummary::roomId)
                .containsExactly(sessionId, 1L);
    }

    @Test
    void roomDetailShowsCharacterRelationAndActiveSession() throws Exception {
        mvc.perform(get("/api/rooms/1").with(login(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(ROOM.description()))
                .andExpect(jsonPath("$.characters[0].personality[0]").value("츤데레"))
                .andExpect(jsonPath("$.characters[0].relation.level").value(1))
                .andExpect(jsonPath("$.characters[0].relation.levelName").value("아는 사이"))
                .andExpect(
                        jsonPath("$.characters[0].relation.totalStudySeconds").value(0))
                .andExpect(jsonPath("$.activeSessionId").value(nullValue()));

        long sessionId = start(userId);

        mvc.perform(get("/api/rooms/1").with(login(userId)))
                .andExpect(jsonPath("$.activeSessionId").value(sessionId));
        expectError(mvc.perform(get("/api/rooms/99").with(login(userId))), 404, "ROOM_NOT_FOUND");
    }

    // ---- 자동 마감 배치

    @Test
    void autoCloseClosesStaleSessionAtLastHeartbeat() throws Exception {
        long sessionId = start(userId);
        event(sessionId, "FOCUS", startedAt(sessionId), 201);
        // 8시간 전에 시작해 1시간 공부하고 끊긴 것으로 옮긴다
        jdbc.update(
                "UPDATE study_sessions SET started_at = started_at - interval '8 hours',"
                        + " last_heartbeat_at = started_at - interval '7 hours' WHERE id = ?",
                sessionId);
        jdbc.update(
                "UPDATE session_events SET occurred_at = occurred_at - interval '8 hours' WHERE session_id = ?",
                sessionId);
        long freshSessionId = start(newUser());

        service.autoCloseStale();

        assertThat(jdbc.queryForMap(
                        "SELECT end_type, ended_at = last_heartbeat_at AS at_heartbeat, focus_seconds, closing_line"
                                + " FROM study_sessions WHERE id = ?",
                        sessionId))
                .containsEntry("end_type", "AUTO_CLOSED")
                .containsEntry("at_heartbeat", true)
                .containsEntry("focus_seconds", 3600)
                .containsEntry("closing_line", CLOSING_LINE);
        verify(relationCommands).settleStudyTime(userId, 1L, sessionId, 3600);
        verify(closingHandler).close(new SessionClosing(sessionId, userId, 1L, "채린", "AUTO_CLOSED", 3600));
        assertThat(sessionQuery.find(freshSessionId)).get().matches(SessionInfo::inProgress);
    }

    // ---- 도우미

    long newUser() {
        return jdbc.queryForObject("INSERT INTO users (nickname) VALUES ('세션테스트') RETURNING id", Long.class);
    }

    static RequestPostProcessor login(long userId) {
        return jwt().jwt(j -> j.subject(String.valueOf(userId)));
    }

    ResultActions startRequest(long userId, int targetMinutes) throws Exception {
        return startRequest(userId, "{\"roomId\":1,\"characterId\":1,\"targetMinutes\":" + targetMinutes + "}");
    }

    ResultActions startRequest(long userId, String body) throws Exception {
        return mvc.perform(post("/api/sessions")
                .with(login(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    long start(long userId) throws Exception {
        String body = startRequest(userId, 50)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.sessionId")).longValue();
    }

    Instant startedAt(long sessionId) {
        return sessionQuery.find(sessionId).orElseThrow().startedAt();
    }

    ResultActions postEvent(long userId, long sessionId, String type, Instant occurredAt) throws Exception {
        return mvc.perform(post("/api/sessions/{id}/events", sessionId)
                .with(login(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"" + type + "\",\"occurredAt\":\"" + occurredAt + "\"}"));
    }

    long event(long sessionId, String type, Instant occurredAt, int expectedStatus) throws Exception {
        String body = postEvent(userId, sessionId, type, occurredAt)
                .andExpect(status().is(expectedStatus))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.eventId")).longValue();
    }

    ResultActions end(long sessionId, int studySeconds, int presentSeconds) throws Exception {
        return mvc.perform(post("/api/sessions/{id}/end", sessionId)
                .with(login(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studySeconds\":" + studySeconds + ",\"presentSeconds\":" + presentSeconds + "}"));
    }

    static void expectReport(ResultActions result, long sessionId) throws Exception {
        result.andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.roomName").value(ROOM.name()))
                .andExpect(jsonPath("$.startedAt").isString())
                .andExpect(jsonPath("$.endedAt").isString())
                .andExpect(jsonPath("$.endType").value("NORMAL"))
                .andExpect(jsonPath("$.studySeconds").value(3300))
                .andExpect(jsonPath("$.presentSeconds").value(2880))
                .andExpect(jsonPath("$.awaySeconds").value(420))
                .andExpect(jsonPath("$.returnCount").value(1))
                .andExpect(jsonPath("$.character.id").value(1))
                .andExpect(jsonPath("$.character.name").value("채린"))
                .andExpect(jsonPath("$.relation.pointsAfter").value(55))
                .andExpect(jsonPath("$.relation.levelName").value("아는 사이"))
                .andExpect(jsonPath("$.closingLine").value(CLOSING_LINE));
    }

    static ResultActions expectError(ResultActions result, int httpStatus, String code) throws Exception {
        return result.andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code));
    }
}
