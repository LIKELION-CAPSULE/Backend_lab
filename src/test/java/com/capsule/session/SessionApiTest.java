package com.capsule.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
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
import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.content.api.ContentTypes.ExpressionRef;
import com.capsule.content.api.ContentTypes.MateInfo;
import com.capsule.content.api.ContentTypes.ModelInfo;
import com.capsule.content.api.ContentTypes.MotionRef;
import com.capsule.content.api.ContentTypes.Reaction;
import com.capsule.content.api.MateQuery;
import com.capsule.content.api.ReactionQuery;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
            CHAERIN_MODEL,
            "https://cdn.capsule.invalid/chaerin-fullbody.webp");
    static final MateInfo CHAERIN_MATE = new MateInfo(
            1,
            "채린",
            "시험기간 밤, 각자 공부하며 연결 중인 같은 반 친구",
            "ALL",
            List.of("같은반", "시험기간"),
            "https://cdn.capsule.invalid/chaerin/full.png",
            "https://cdn.capsule.invalid/chaerin/card.png",
            null,
            null,
            null,
            null,
            CHAERIN_MODEL,
            List.of(),
            false,
            true);
    static final MateInfo COMING_SOON = new MateInfo(
            2, "준비 중", null, "ALL", List.of(), null, null, null, null, null, null, null, List.of(), true, true);
    static final CharacterInfo COMING_SOON_CHARACTER =
            new CharacterInfo(2, 0, "준비 중", null, List.of(), null, true, null, null);
    static final AvatarInfo AVATAR = new AvatarInfo(
            1,
            "기본 아바타",
            "https://cdn.capsule.invalid/avatar1.png",
            true,
            new ModelInfo("https://cdn.capsule.invalid/live2d/avatar1/v1/avatar1.model3.json", 1.0, 0, 0));
    static final StudySettlement SETTLEMENT = new StudySettlement(1, 1, "아는 사이", 0, 55, null, false, 1, "아는 사이", 3600);
    static final String CLOSING_LINE = "오늘도 같이 공부해서 좋았어.";
    static final SessionClosingHandler.Draft DRAFT = new SessionClosingHandler.Draft() {};
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
    MateQuery mateQuery;

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
        given(mateQuery.find(1L)).willReturn(Optional.of(CHAERIN_MATE));
        given(mateQuery.find(2L)).willReturn(Optional.of(COMING_SOON));
        given(mateQuery.activeMates()).willReturn(List.of(CHAERIN_MATE, COMING_SOON));
        given(characterQuery.find(1L)).willReturn(Optional.of(CHAERIN));
        given(characterQuery.find(2L)).willReturn(Optional.of(COMING_SOON_CHARACTER));
        given(relationQuery.view(anyLong(), eq(1L)))
                .willReturn(new RelationView(1, 1, "아는 사이", 50, 0, null, 1, "아는 사이", 0, 3600));
        given(relationQuery.settlementOf(anyLong(), anyLong(), anyLong())).willReturn(SETTLEMENT);
        given(relationCommands.settleStudy(anyLong(), anyLong(), anyLong(), anyInt(), anyInt()))
                .willReturn(SETTLEMENT);
        // 대화 마무리 초안(LLM)은 DB 트랜잭션 밖에서 만들고, 저장 쪽에 그대로 넘긴다 (개발 서버 풀 2)
        given(closingHandler.draft(any())).willAnswer(inv -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isFalse();
            return DRAFT;
        });
        given(closingHandler.close(any(), any())).willReturn(CLOSING_LINE);
        given(reactionQuery.pick(anyLong(), anyString(), any())).willReturn(Reaction.NONE);
    }

    // ---- 16 연결 시작

    @Test
    void startCreatesConnectionWithScene() throws Exception {
        startRequest(userId, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.connectionId").isNumber())
                .andExpect(jsonPath("$.data.startedAt").isString())
                .andExpect(jsonPath("$.data.targetMinutes").doesNotExist()) // 시간 제한 없음 (2026-11-23)
                .andExpect(jsonPath("$.data.room").doesNotExist())
                .andExpect(jsonPath("$.data.mate.name").value("채린"))
                .andExpect(jsonPath("$.data.mate.format").value("CUBISM"))
                .andExpect(jsonPath("$.data.mate.modelJsonUrl").value(CHAERIN_MODEL.modelJsonUrl()))
                .andExpect(jsonPath("$.data.mate.offsetY").value(12))
                .andExpect(jsonPath("$.data.avatar.profileImageUrl").value(AVATAR.thumbnailUrl()))
                .andExpect(jsonPath("$.data.avatar.format").value("CUBISM"))
                .andExpect(jsonPath("$.data.me.nickname").value("승희"))
                .andExpect(jsonPath("$.data.me.profileImageUrl").value(AVATAR.thumbnailUrl()))
                .andExpect(jsonPath("$.data.serverTime").isString());

        verify(relationCommands).meet(userId, 1L);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM connections WHERE user_id = ? AND character_id = 1"
                                + " AND last_heartbeat_at IS NOT NULL",
                        Integer.class,
                        userId))
                .isEqualTo(1);
    }

    @Test
    void startRejectsInvalidRequests() throws Exception {
        expectError(startRequest(userId, 99), 404, "MATE_NOT_FOUND");
        expectError(startRequest(userId, 2), 409, "MATE_COMING_SOON");
        expectError(startRequest(userId, "{}"), 400, "INVALID_REQUEST");

        given(userQuery.find(userId)).willReturn(Optional.of(new UserProfile(userId, "승희", null, null, null, true)));
        expectError(startRequest(userId, 1), 403, "ONBOARDING_REQUIRED");
    }

    @Test
    void startWhileInProgressPointsToExistingSession() throws Exception {
        long sessionId = start(userId);

        expectError(startRequest(userId, 1), 409, "CONNECTION_ALREADY_IN_PROGRESS")
                .andExpect(jsonPath("$.detail.connectionId").value(sessionId));
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
                .andExpect(jsonPath("$.data.characters.length()").value(1))
                .andExpect(jsonPath("$.data.characters[0].characterId").value(1))
                .andExpect(jsonPath("$.data.characters[0].motion.file").value("motions/wave.motion3.json"))
                .andExpect(jsonPath("$.data.characters[0].expression.name").value("smile"))
                .andExpect(jsonPath("$.data.characters[0].affinity.delta").value(5))
                .andExpect(jsonPath("$.data.characters[0].affinity.points").value(5))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long focus = ((Number) JsonPath.read(first, "$.data.eventId")).longValue();

        // 재전송: 호감도는 다시 반영하지 않고 기록된 변화를 읽어서 같은 모양으로
        postEvent(userId, sessionId, "FOCUS", t0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventId").value(focus))
                .andExpect(jsonPath("$.data.characters[0].affinity.delta").value(5));
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
                .andExpect(jsonPath("$.data.eventId").value(nullValue()))
                .andExpect(jsonPath("$.data.characters").isEmpty());
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
        expectError(postEvent(newUser(), sessionId, "FOCUS", t0), 404, "CONNECTION_NOT_FOUND");
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
                .andExpect(jsonPath("$.data.eventId").value(nullValue()))
                .andExpect(jsonPath("$.data.characters").isEmpty());
        event(sessionId, "HEAD_TURN", t0.plusSeconds(60), 201);
        event(sessionId, "YAWN", t0.plusSeconds(90), 201); // 1분 지남
        assertThat(event(sessionId, "YAWN", t0.plusSeconds(30), 200)).isEqualTo(yawn); // 재전송은 쿨다운보다 먼저

        // 휴식 중 동작 이벤트는 무시. 휴식 뒤 FOCUS는 그대로 휴식 뒤로 판정
        event(sessionId, "BREAK", t0.plusSeconds(120), 201);
        postEvent(userId, sessionId, "WAVE", t0.plusSeconds(130))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventId").value(nullValue()));
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
                "UPDATE connections SET started_at = started_at - interval '30 minutes',"
                        + " last_heartbeat_at = started_at - interval '20 minutes' WHERE id = ?",
                sessionId);
        jdbc.update(
                "UPDATE connection_events SET occurred_at = occurred_at - interval '30 minutes' WHERE connection_id = ?",
                sessionId);
        clearInvocations(relationCommands);

        mvc.perform(post("/api/connections/{id}/resume", sessionId).with(login(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connectionId").value(sessionId))
                .andExpect(jsonPath("$.data.startedAt").doesNotExist())
                .andExpect(jsonPath("$.data.mate.name").value("채린"))
                .andExpect(jsonPath("$.data.avatar.id").value(1))
                .andExpect(jsonPath("$.data.studySeconds").value(600))
                .andExpect(jsonPath("$.data.breakSeconds").value(greaterThanOrEqualTo(1200)));

        assertThat(jdbc.queryForList(
                        "SELECT type FROM connection_events WHERE connection_id = ? ORDER BY occurred_at, id",
                        String.class,
                        sessionId))
                .containsExactly("FOCUS", "BREAK", "FOCUS");
        verifyNoInteractions(relationCommands);
    }

    @Test
    void heartbeatRefreshesLastHeartbeatAndStoresStudyTime() throws Exception {
        long sessionId = start(userId);
        jdbc.update(
                "UPDATE connections SET started_at = started_at - interval '1 hour', last_heartbeat_at = NULL"
                        + " WHERE id = ?",
                sessionId);

        heartbeat(sessionId, 1800, 1620).andExpect(status().isNoContent());

        assertThat(jdbc.queryForMap(
                        "SELECT last_heartbeat_at IS NOT NULL AS beat, heartbeat_study_seconds, heartbeat_present_seconds"
                                + " FROM connections WHERE id = ?",
                        sessionId))
                .containsEntry("beat", true)
                .containsEntry("heartbeat_study_seconds", 1800)
                .containsEntry("heartbeat_present_seconds", 1620);

        // 값이 틀리면 400이지만 생존 신호는 갱신하고, 저장된 값은 그대로
        jdbc.update("UPDATE connections SET last_heartbeat_at = NULL WHERE id = ?", sessionId);
        expectError(heartbeat(sessionId, 3700, 100), 400, "INVALID_STUDY_TIME"); // 경과 시간(1시간) 초과
        expectError(heartbeat(sessionId, 1800, 1900), 400, "INVALID_STUDY_TIME");
        expectError(heartbeat(sessionId, 1800, -1), 400, "INVALID_STUDY_TIME");
        expectError(
                mvc.perform(post("/api/connections/{id}/heartbeat", sessionId)
                        .with(login(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")),
                400,
                "INVALID_STUDY_TIME");
        expectError(
                mvc.perform(post("/api/connections/{id}/heartbeat", sessionId).with(login(userId))),
                400,
                "INVALID_STUDY_TIME"); // 본문 없음
        assertThat(jdbc.queryForMap(
                        "SELECT last_heartbeat_at IS NOT NULL AS beat, heartbeat_study_seconds, heartbeat_present_seconds"
                                + " FROM connections WHERE id = ?",
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
                "UPDATE connections SET started_at = started_at - interval '30 minutes',"
                        + " last_heartbeat_at = started_at - interval '20 minutes',"
                        + " heartbeat_study_seconds = 0, heartbeat_present_seconds = 0 WHERE id = ?",
                sessionId);
        jdbc.update(
                "UPDATE connection_events SET occurred_at = occurred_at - interval '30 minutes' WHERE connection_id = ?",
                sessionId);

        mvc.perform(post("/api/connections/{id}/resume", sessionId).with(login(userId)))
                .andExpect(jsonPath("$.data.studySeconds").value(600));

        assertThat(jdbc.queryForMap(
                        "SELECT heartbeat_study_seconds, heartbeat_present_seconds FROM connections WHERE id = ?",
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
        jdbc.update("UPDATE connections SET started_at = started_at - interval '1 hour' WHERE id = ?", sessionId);
        jdbc.update(
                "UPDATE connection_events SET occurred_at = occurred_at - interval '1 hour' WHERE connection_id = ?",
                sessionId);

        expectError(end(sessionId, 3700, 100), 400, "INVALID_STUDY_TIME"); // 경과 시간(1시간) 초과
        expectError(end(sessionId, 3300, 3400), 400, "INVALID_STUDY_TIME");
        expectError(end(sessionId, 3300, -1), 400, "INVALID_STUDY_TIME");

        expectReport(end(sessionId, 3300, 2880).andExpect(status().isOk()), sessionId);
        expectReport(end(sessionId, 10, 10).andExpect(status().isOk()), sessionId); // 다시 불러도 같은 결과
        expectReport(
                mvc.perform(get("/api/connections/{id}", sessionId).with(login(userId)))
                        .andExpect(status().isOk()),
                sessionId);

        verify(relationCommands, times(1)).settleStudy(anyLong(), anyLong(), anyLong(), anyInt(), anyInt());
        verify(relationCommands).settleStudy(userId, 1L, sessionId, 3300, 2880);
        SessionClosing closing = new SessionClosing(sessionId, userId, 1L, "채린", "NORMAL", 3300, null);
        // 초안은 한 번만: 400 요청과 두 번째 종료에는 LLM을 부르지 않는다
        verify(closingHandler, times(1)).draft(any());
        verify(closingHandler).draft(closing);
        verify(closingHandler, times(1)).close(any(), any());
        verify(closingHandler).close(eq(closing), same(DRAFT));
        assertThat(jdbc.queryForMap(
                        "SELECT end_type, focus_seconds, distract_seconds, return_count, closing_line"
                                + " FROM connections WHERE id = ?",
                        sessionId))
                .containsEntry("end_type", "NORMAL")
                .containsEntry("focus_seconds", 2880)
                .containsEntry("distract_seconds", 420)
                .containsEntry("return_count", 1)
                .containsEntry("closing_line", CLOSING_LINE);

        expectError(heartbeat(sessionId, 10, 10), 409, "CONNECTION_ENDED");
        expectError(
                mvc.perform(post("/api/connections/{id}/resume", sessionId).with(login(userId))),
                409,
                "CONNECTION_ENDED");
        expectError(postEvent(userId, sessionId, "FOCUS", Instant.now()), 409, "CONNECTION_ENDED");
    }

    @Test
    void reportOfInProgressSessionIsConflict() throws Exception {
        long sessionId = start(userId);

        expectError(
                mvc.perform(get("/api/connections/{id}", sessionId).with(login(userId))),
                409,
                "CONNECTION_IN_PROGRESS");
        expectError(
                mvc.perform(get("/api/connections/{id}", sessionId).with(login(newUser()))),
                404,
                "CONNECTION_NOT_FOUND");
    }

    // ---- 11 홈 · ActiveSessionLookup

    @Test
    void homeShowsMatesLastMateAndActiveConnection() throws Exception {
        mvc.perform(get("/api/home").with(login(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeConnection").value(nullValue()))
                .andExpect(jsonPath("$.data.lastMate").value(nullValue())) // 첫 방문
                .andExpect(jsonPath("$.data.rooms").doesNotExist())
                .andExpect(jsonPath("$.data.mates.length()").value(2))
                .andExpect(jsonPath("$.data.mates[0].name").value("채린"))
                .andExpect(jsonPath("$.data.mates[0].oneLiner").value(CHAERIN_MATE.oneLiner()))
                .andExpect(jsonPath("$.data.mates[0].hashtags[0]").value("같은반"))
                .andExpect(jsonPath("$.data.mates[0].cardImageUrl").value(CHAERIN_MATE.cardImageUrl()))
                .andExpect(jsonPath("$.data.mates[0].comingSoon").value(false))
                .andExpect(jsonPath("$.data.mates[1].comingSoon").value(true));
        assertThat(activeSessionLookup.find(userId)).isEmpty();

        long sessionId = start(userId);

        mvc.perform(get("/api/home").with(login(userId)))
                .andExpect(jsonPath("$.data.activeConnection.connectionId").value(sessionId))
                .andExpect(jsonPath("$.data.activeConnection.mateId").value(1))
                .andExpect(jsonPath("$.data.activeConnection.mateName").value("채린"))
                .andExpect(jsonPath("$.data.lastMate.id").value(1))
                .andExpect(jsonPath("$.data.lastMate.illustrationUrl").value(CHAERIN_MATE.illustrationUrl()));
        assertThat(activeSessionLookup.find(userId))
                .get()
                .extracting(ActiveSessionSummary::connectionId, ActiveSessionSummary::mateId)
                .containsExactly(sessionId, 1L);
    }

    @Test
    void roomDetailIsGone() throws Exception {
        // 12 스터디룸 상세 삭제 (2026-11-23)
        expectError(mvc.perform(get("/api/rooms/1").with(login(userId))), 404, "NOT_FOUND");
    }

    // ---- 자동 마감 배치

    @Test
    void autoCloseClosesStaleSessionAtLastHeartbeat() throws Exception {
        long sessionId = start(userId);
        event(sessionId, "FOCUS", startedAt(sessionId), 201);
        // 8시간 전에 시작해 1시간 공부하고 끊긴 것으로 옮긴다
        jdbc.update(
                "UPDATE connections SET started_at = started_at - interval '8 hours',"
                        + " last_heartbeat_at = started_at - interval '7 hours' WHERE id = ?",
                sessionId);
        jdbc.update(
                "UPDATE connection_events SET occurred_at = occurred_at - interval '8 hours' WHERE connection_id = ?",
                sessionId);
        long freshSessionId = start(newUser());

        service.autoCloseStale();

        assertThat(jdbc.queryForMap(
                        "SELECT end_type, ended_at = last_heartbeat_at AS at_heartbeat, focus_seconds, closing_line"
                                + " FROM connections WHERE id = ?",
                        sessionId))
                .containsEntry("end_type", "AUTO_CLOSED")
                .containsEntry("at_heartbeat", true)
                .containsEntry("focus_seconds", 3600)
                .containsEntry("closing_line", CLOSING_LINE);
        verify(relationCommands).settleStudy(userId, 1L, sessionId, 3600, 3600);
        verify(closingHandler)
                .close(eq(new SessionClosing(sessionId, userId, 1L, "채린", "AUTO_CLOSED", 3600, null)), same(DRAFT));
        assertThat(sessionQuery.find(freshSessionId)).get().matches(SessionInfo::inProgress);
    }

    @Test
    void autoCloseUsesLastHeartbeatStudyTime() throws Exception {
        long sessionId = start(userId);
        event(sessionId, "FOCUS", startedAt(sessionId), 201);
        jdbc.update(
                "UPDATE connections SET started_at = started_at - interval '8 hours',"
                        + " last_heartbeat_at = started_at - interval '7 hours',"
                        + " heartbeat_study_seconds = 3000, heartbeat_present_seconds = 2700 WHERE id = ?",
                sessionId);
        jdbc.update(
                "UPDATE connection_events SET occurred_at = occurred_at - interval '8 hours' WHERE connection_id = ?",
                sessionId);

        service.autoCloseStale();

        // 이벤트로 계산하면 3600초지만, 마지막 heartbeat가 보낸 값으로 기록
        assertThat(jdbc.queryForMap(
                        "SELECT end_type, focus_seconds, distract_seconds FROM connections WHERE id = ?", sessionId))
                .containsEntry("end_type", "AUTO_CLOSED")
                .containsEntry("focus_seconds", 2700)
                .containsEntry("distract_seconds", 300);
        verify(relationCommands).settleStudy(userId, 1L, sessionId, 3000, 2700);
        verify(closingHandler)
                .close(eq(new SessionClosing(sessionId, userId, 1L, "채린", "AUTO_CLOSED", 3000, null)), same(DRAFT));
    }

    // ---- 도우미

    long newUser() {
        return jdbc.queryForObject("INSERT INTO users (nickname) VALUES ('세션테스트') RETURNING id", Long.class);
    }

    static RequestPostProcessor login(long userId) {
        return jwt().jwt(j -> j.subject(String.valueOf(userId)));
    }

    ResultActions startRequest(long userId, long mateId) throws Exception {
        return startRequest(userId, "{\"mateId\":" + mateId + "}");
    }

    ResultActions startRequest(long userId, String body) throws Exception {
        return mvc.perform(post("/api/connections")
                .with(login(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    long start(long userId) throws Exception {
        String body = startRequest(userId, 1)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.connectionId")).longValue();
    }

    Instant startedAt(long sessionId) {
        return sessionQuery.find(sessionId).orElseThrow().startedAt();
    }

    ResultActions postEvent(long userId, long sessionId, String type, Instant occurredAt) throws Exception {
        return mvc.perform(post("/api/connections/{id}/events", sessionId)
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
        return ((Number) JsonPath.read(body, "$.data.eventId")).longValue();
    }

    ResultActions heartbeat(long sessionId, int studySeconds, int presentSeconds) throws Exception {
        return mvc.perform(post("/api/connections/{id}/heartbeat", sessionId)
                .with(login(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studySeconds\":" + studySeconds + ",\"presentSeconds\":" + presentSeconds + "}"));
    }

    ResultActions end(long sessionId, int studySeconds, int presentSeconds) throws Exception {
        return mvc.perform(post("/api/connections/{id}/end", sessionId)
                .with(login(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studySeconds\":" + studySeconds + ",\"presentSeconds\":" + presentSeconds + "}"));
    }

    static void expectReport(ResultActions result, long sessionId) throws Exception {
        result.andExpect(jsonPath("$.data.connectionId").value(sessionId))
                .andExpect(jsonPath("$.data.roomName").doesNotExist())
                .andExpect(jsonPath("$.data.startedAt").isString())
                .andExpect(jsonPath("$.data.endedAt").isString())
                .andExpect(jsonPath("$.data.endType").value("NORMAL"))
                .andExpect(jsonPath("$.data.studySeconds").value(3300))
                .andExpect(jsonPath("$.data.presentSeconds").value(2880))
                .andExpect(jsonPath("$.data.awaySeconds").value(420))
                .andExpect(jsonPath("$.data.returnCount").value(1))
                .andExpect(jsonPath("$.data.mate.id").value(1))
                .andExpect(jsonPath("$.data.mate.name").value("채린"))
                .andExpect(jsonPath("$.data.relation.pointsAfter").value(55))
                .andExpect(jsonPath("$.data.relation.levelName").value("아는 사이"))
                .andExpect(jsonPath("$.data.closingLine").value(CLOSING_LINE));
    }

    static ResultActions expectError(ResultActions result, int httpStatus, String code) throws Exception {
        return result.andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code));
    }
}
