package com.capsule.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.capsule.content.api.ContentTypes.ExpressionRef;
import com.capsule.content.api.ContentTypes.ModelInfo;
import com.capsule.content.api.ContentTypes.MotionRef;
import com.capsule.content.api.ContentTypes.Reaction;
import com.capsule.content.api.ContentTypes.RoomInfo;
import com.capsule.content.api.ReactionQuery;
import com.capsule.content.api.RoomQuery;
import com.capsule.relation.api.RelationCommands;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.api.RelationTypes.AffinityChange;
import com.capsule.relation.api.RelationTypes.RelationView;
import com.capsule.relation.api.RelationTypes.SessionEventRef;
import com.capsule.relation.api.RelationTypes.StudySettlement;
import com.capsule.session.api.SessionClosingHandler;
import com.capsule.session.api.SessionQuery;
import com.capsule.session.api.SessionTypes.SessionClosing;
import com.capsule.session.api.SessionTypes.SessionEventInfo;
import com.capsule.session.api.SessionTypes.SessionInfo;
import com.capsule.session.service.SessionService;
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
    static final StudySettlement SETTLEMENT = new StudySettlement(1, 1, "아는 사이", 0, 55, null, false, 1, "아는 사이", 3600);
    static final String CLOSING_LINE = "오늘도 같이 공부해서 좋았어.";
    static final Reaction GREETING_REACTION = new Reaction(
            new MotionRef("TapBody", "motions/wave.motion3.json"), new ExpressionRef("smile", "exp/smile.exp3.json"));

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
    ReactionQuery reactionQuery;

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
        given(relationQuery.view(anyLong(), eq(1L)))
                .willReturn(new RelationView(1, 1, "아는 사이", 50, 0, null, 1, "아는 사이", 0, 3600));
        given(relationQuery.settlementOf(anyLong(), anyLong(), anyLong())).willReturn(SETTLEMENT);
        given(relationCommands.settleStudy(anyLong(), anyLong(), anyLong(), anyInt(), anyInt()))
                .willReturn(SETTLEMENT);
        given(closingHandler.close(any())).willReturn(CLOSING_LINE);
        given(reactionQuery.pick(anyLong(), anyString(), any())).willReturn(Reaction.NONE);
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
    void eventReturnsCharacterMotionAndAffinity() throws Exception {
        long sessionId = start(userId);
        Instant t0 = startedAt(sessionId);
        given(reactionQuery.pick(1L, "GREETING", null)).willReturn(GREETING_REACTION);
        given(relationCommands.applySessionEvent(any())).willReturn(new AffinityChange(5, 5, 1, false, 1, "아는 사이"));
        given(relationQuery.changeOf(any())).willReturn(new AffinityChange(5, 5, 1, false, 1, "아는 사이"));

        String first = postEvent(userId, sessionId, "FOCUS", t0)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.characters.length()").value(1))
                .andExpect(jsonPath("$.characters[0].characterId").value(1))
                .andExpect(jsonPath("$.characters[0].motion.file").value("motions/wave.motion3.json"))
                .andExpect(jsonPath("$.characters[0].expression.name").value("smile"))
                .andExpect(jsonPath("$.characters[0].affinity.delta").value(5))
                .andExpect(jsonPath("$.characters[0].affinity.points").value(5))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long focus = ((Number) JsonPath.read(first, "$.eventId")).longValue();

        // 재전송: 호감도는 다시 반영하지 않고 기록된 변화를 읽어서 같은 모양으로
        postEvent(userId, sessionId, "FOCUS", t0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(focus))
                .andExpect(jsonPath("$.characters[0].affinity.delta").value(5));
        SessionEventRef ref = new SessionEventRef(userId, 1, sessionId, focus, "FOCUS", true, null);
        verify(relationCommands, times(1)).applySessionEvent(any());
        verify(relationQuery).changeOf(ref);

        event(sessionId, "AWAY", t0.plusSeconds(60), 201);
        verify(reactionQuery).pick(1L, "AWAY", 1); // 18에서는 강도 1
        event(sessionId, "BREAK", t0.plusSeconds(120), 201);
        event(sessionId, "FOCUS", t0.plusSeconds(180), 201);
        verify(reactionQuery).pick(1L, "BREAK_END", null);
    }

    @Test
    void eventsAreSavedOnceAndIgnoredDuringBreak() throws Exception {
        long sessionId = start(userId);
        Instant t0 = startedAt(sessionId);

        long focus = event(sessionId, "FOCUS", t0, 201);
        assertThat(event(sessionId, "FOCUS", t0, 200)).isEqualTo(focus); // 재전송
        verify(relationCommands)
                .applySessionEvent(new SessionEventRef(userId, 1, sessionId, focus, "FOCUS", true, null));

        event(sessionId, "BREAK", t0.plusSeconds(60), 201);
        postEvent(userId, sessionId, "AWAY", t0.plusSeconds(70))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(nullValue()))
                .andExpect(jsonPath("$.characters").isEmpty());
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

    @Test
    void stateEventEndingAnAwayCarriesHowLongItWas() throws Exception {
        long sessionId = start(userId);
        Instant t0 = startedAt(sessionId);
        event(sessionId, "FOCUS", t0, 201);
        long away = event(sessionId, "AWAY", t0.plusSeconds(10), 201);
        verify(relationCommands)
                .applySessionEvent(new SessionEventRef(userId, 1, sessionId, away, "AWAY", false, null));
        event(sessionId, "YAWN", t0.plusSeconds(20), 201); // 동작 이벤트는 자리 비움을 끝내지 않는다
        long back = event(sessionId, "RETURN", t0.plusSeconds(80), 201);
        // relation이 1분 넘게 비웠는지 보고 감점한다 (호감도 최종안)
        verify(relationCommands)
                .applySessionEvent(new SessionEventRef(userId, 1, sessionId, back, "RETURN", false, 70));
        // 늦게 도착한 이벤트는 이미 끝난 자리 비움을 다시 끝내지 않는다 (두 번 감점 방지)
        long late = event(sessionId, "BREAK", t0.plusSeconds(50), 201);
        verify(relationCommands)
                .applySessionEvent(new SessionEventRef(userId, 1, sessionId, late, "BREAK", false, null));
    }

    @Test
    void motionEventsHaveCooldownAndAreIgnoredDuringBreak() throws Exception {
        long sessionId = start(userId);
        Instant t0 = startedAt(sessionId);
        event(sessionId, "FOCUS", t0, 201);

        long yawn = event(sessionId, "YAWN", t0.plusSeconds(30), 201);
        verify(relationCommands)
                .applySessionEvent(new SessionEventRef(userId, 1, sessionId, yawn, "YAWN", false, null));
        // 같은 동작 1분 안 → 무시 (서버 쿨다운). 다른 동작은 따로 셈
        postEvent(userId, sessionId, "YAWN", t0.plusSeconds(60))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(nullValue()))
                .andExpect(jsonPath("$.characters").isEmpty());
        event(sessionId, "HEAD_TURN", t0.plusSeconds(60), 201);
        event(sessionId, "YAWN", t0.plusSeconds(90), 201); // 1분 지남
        assertThat(event(sessionId, "YAWN", t0.plusSeconds(30), 200)).isEqualTo(yawn); // 재전송은 쿨다운보다 먼저

        // 휴식 중 동작 이벤트는 무시. 휴식 뒤 FOCUS는 그대로 휴식 뒤로 판정
        event(sessionId, "BREAK", t0.plusSeconds(120), 201);
        postEvent(userId, sessionId, "WAVE", t0.plusSeconds(130))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(nullValue()));
        long focusAfterBreak = event(sessionId, "FOCUS", t0.plusSeconds(180), 201);
        assertThat(sessionQuery.findEvent(sessionId, focusAfterBreak))
                .get()
                .extracting(SessionEventInfo::afterBreak)
                .isEqualTo(true);
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
    void heartbeatRefreshesLastHeartbeatAndStoresStudyTime() throws Exception {
        long sessionId = start(userId);
        jdbc.update(
                "UPDATE study_sessions SET started_at = started_at - interval '1 hour', last_heartbeat_at = NULL"
                        + " WHERE id = ?",
                sessionId);

        heartbeat(sessionId, 1800, 1620).andExpect(status().isNoContent());

        assertThat(jdbc.queryForMap(
                        "SELECT last_heartbeat_at IS NOT NULL AS beat, heartbeat_study_seconds, heartbeat_present_seconds"
                                + " FROM study_sessions WHERE id = ?",
                        sessionId))
                .containsEntry("beat", true)
                .containsEntry("heartbeat_study_seconds", 1800)
                .containsEntry("heartbeat_present_seconds", 1620);

        // 값이 틀리면 400이지만 생존 신호는 갱신하고, 저장된 값은 그대로
        jdbc.update("UPDATE study_sessions SET last_heartbeat_at = NULL WHERE id = ?", sessionId);
        expectError(heartbeat(sessionId, 3700, 100), 400, "INVALID_STUDY_TIME"); // 경과 시간(1시간) 초과
        expectError(heartbeat(sessionId, 1800, 1900), 400, "INVALID_STUDY_TIME");
        expectError(heartbeat(sessionId, 1800, -1), 400, "INVALID_STUDY_TIME");
        expectError(
                mvc.perform(post("/api/sessions/{id}/heartbeat", sessionId)
                        .with(login(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")),
                400,
                "INVALID_STUDY_TIME");
        expectError(
                mvc.perform(post("/api/sessions/{id}/heartbeat", sessionId).with(login(userId))),
                400,
                "INVALID_STUDY_TIME"); // 본문 없음
        assertThat(jdbc.queryForMap(
                        "SELECT last_heartbeat_at IS NOT NULL AS beat, heartbeat_study_seconds, heartbeat_present_seconds"
                                + " FROM study_sessions WHERE id = ?",
                        sessionId))
                .containsEntry("beat", true)
                .containsEntry("heartbeat_study_seconds", 1800)
                .containsEntry("heartbeat_present_seconds", 1620);
    }

    @Test
    void resumeRefreshesStoredStudyTimeSoAutoCloseMatchesResume() throws Exception {
        long sessionId = start(userId);
        event(sessionId, "FOCUS", startedAt(sessionId), 201);
        // 30분 전 시작, 10분 공부 뒤 끊김. 끊기기 전 마지막 heartbeat 값은 0초로 남아 있음
        jdbc.update(
                "UPDATE study_sessions SET started_at = started_at - interval '30 minutes',"
                        + " last_heartbeat_at = started_at - interval '20 minutes',"
                        + " heartbeat_study_seconds = 0, heartbeat_present_seconds = 0 WHERE id = ?",
                sessionId);
        jdbc.update(
                "UPDATE session_events SET occurred_at = occurred_at - interval '30 minutes' WHERE session_id = ?",
                sessionId);

        mvc.perform(post("/api/sessions/{id}/resume", sessionId).with(login(userId)))
                .andExpect(jsonPath("$.studySeconds").value(600));

        assertThat(jdbc.queryForMap(
                        "SELECT heartbeat_study_seconds, heartbeat_present_seconds FROM study_sessions WHERE id = ?",
                        sessionId))
                .containsEntry("heartbeat_study_seconds", 600)
                .containsEntry("heartbeat_present_seconds", 600);
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

        verify(relationCommands, times(1)).settleStudy(anyLong(), anyLong(), anyLong(), anyInt(), anyInt());
        verify(relationCommands).settleStudy(userId, 1L, sessionId, 3300, 2880);
        verify(closingHandler, times(1)).close(any());
        verify(closingHandler).close(new SessionClosing(sessionId, userId, 1L, "채린", "NORMAL", 3300, 50));
        assertThat(jdbc.queryForMap(
                        "SELECT end_type, focus_seconds, distract_seconds, return_count, closing_line"
                                + " FROM study_sessions WHERE id = ?",
                        sessionId))
                .containsEntry("end_type", "NORMAL")
                .containsEntry("focus_seconds", 2880)
                .containsEntry("distract_seconds", 420)
                .containsEntry("return_count", 1)
                .containsEntry("closing_line", CLOSING_LINE);

        expectError(heartbeat(sessionId, 10, 10), 409, "SESSION_ENDED");
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
        verify(relationCommands).settleStudy(userId, 1L, sessionId, 3600, 3600);
        verify(closingHandler).close(new SessionClosing(sessionId, userId, 1L, "채린", "AUTO_CLOSED", 3600, 50));
        assertThat(sessionQuery.find(freshSessionId)).get().matches(SessionInfo::inProgress);
    }

    @Test
    void autoCloseUsesLastHeartbeatStudyTime() throws Exception {
        long sessionId = start(userId);
        event(sessionId, "FOCUS", startedAt(sessionId), 201);
        jdbc.update(
                "UPDATE study_sessions SET started_at = started_at - interval '8 hours',"
                        + " last_heartbeat_at = started_at - interval '7 hours',"
                        + " heartbeat_study_seconds = 3000, heartbeat_present_seconds = 2700 WHERE id = ?",
                sessionId);
        jdbc.update(
                "UPDATE session_events SET occurred_at = occurred_at - interval '8 hours' WHERE session_id = ?",
                sessionId);

        service.autoCloseStale();

        // 이벤트로 계산하면 3600초지만, 마지막 heartbeat가 보낸 값으로 기록
        assertThat(jdbc.queryForMap(
                        "SELECT end_type, focus_seconds, distract_seconds FROM study_sessions WHERE id = ?", sessionId))
                .containsEntry("end_type", "AUTO_CLOSED")
                .containsEntry("focus_seconds", 2700)
                .containsEntry("distract_seconds", 300);
        verify(relationCommands).settleStudy(userId, 1L, sessionId, 3000, 2700);
        verify(closingHandler).close(new SessionClosing(sessionId, userId, 1L, "채린", "AUTO_CLOSED", 3000, 50));
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

    ResultActions heartbeat(long sessionId, int studySeconds, int presentSeconds) throws Exception {
        return mvc.perform(post("/api/sessions/{id}/heartbeat", sessionId)
                .with(login(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studySeconds\":" + studySeconds + ",\"presentSeconds\":" + presentSeconds + "}"));
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
