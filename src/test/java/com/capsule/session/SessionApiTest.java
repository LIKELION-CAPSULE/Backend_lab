package com.capsule.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.TestcontainersConfiguration;
import com.capsule.content.api.AvatarQuery;
import com.capsule.content.api.BackgroundQuery;
import com.capsule.content.api.CharacterQuery;
import com.capsule.content.api.ContentTypes.AvatarInfo;
import com.capsule.content.api.ContentTypes.BackgroundInfo;
import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.content.api.ContentTypes.ExpressionRef;
import com.capsule.content.api.ContentTypes.MateInfo;
import com.capsule.content.api.ContentTypes.ModelInfo;
import com.capsule.content.api.ContentTypes.Reaction;
import com.capsule.content.api.ContentTypes.UnlockInfo;
import com.capsule.content.api.MateQuery;
import com.capsule.content.api.ReactionQuery;
import com.capsule.content.api.UnlockQuery;
import com.capsule.relation.api.RelationCommands;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.api.RelationTypes.IntimacySettlement;
import com.capsule.session.api.SessionClosingHandler;
import com.capsule.session.api.SessionQuery;
import com.capsule.session.api.SessionTypes.SessionClosing;
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

/** 연결 API 통합 테스트 (api.md 11 · 16~18 · 21~23). 다른 모듈 api는 전부 목, 연결 행은 실제 DB(캐릭터 1 시드) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SessionApiTest {

    static final ModelInfo CHAERIN_MODEL =
            new ModelInfo("https://cdn.capsule.invalid/live2d/chaerin/v1/chaerin.model3.json", 1.0, 0, 12);
    static final CharacterInfo CHAERIN = new CharacterInfo(
            1,
            0,
            "채린",
            "https://cdn.capsule.invalid/chaerin.png",
            List.of("같은반"),
            "차갑게 굴지만 나한테만 다정한 짝꿍",
            true,
            CHAERIN_MODEL,
            null);
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
            "같은 반 친구",
            null,
            CHAERIN_MODEL,
            List.of("처음 본 사이", "아는 사이", "친구", "단짝", "특별한 사이"),
            false,
            true);
    static final MateInfo COMING_SOON = new MateInfo(
            2, "준비 중", null, "ALL", List.of(), null, null, null, null, null, null, null, List.of(), true, true);
    static final AvatarInfo AVATAR = new AvatarInfo(
            1,
            "기본 아바타",
            "https://cdn.capsule.invalid/avatar1.png",
            true,
            new ModelInfo("https://cdn.capsule.invalid/live2d/avatar1/v1/avatar1.model3.json", 1.0, 0, 0));
    /** 1단계 → 2단계, 이번 인정 2000초 */
    static final IntimacySettlement SETTLEMENT = new IntimacySettlement(1, 2, 2000, 4000, 14000, 2);

    static final UnlockInfo SHY = new UnlockInfo(9, "EXPRESSION", "수줍은 웃음", 2, "shy", null);
    static final String CLOSING_LINE = "오늘도 같이 있어서 좋았어.";
    static final SessionClosingHandler.Draft DRAFT = new SessionClosingHandler.Draft() {};
    static final Reaction SMILE = new Reaction(null, new ExpressionRef("웃음", "exp/smile.exp3.json"));
    static final Reaction POUT = new Reaction(null, new ExpressionRef("삐짐", "exp/pout.exp3.json"));

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

    @MockitoBean // 시드 메이트에 배경 데이터를 넣지 않으려고 (실제 조회는 content BackgroundQueryTest)
    BackgroundQuery backgroundQuery;

    @MockitoBean
    CharacterQuery characterQuery;

    @MockitoBean
    ReactionQuery reactionQuery;

    @MockitoBean
    RelationQuery relationQuery;

    @MockitoBean
    RelationCommands relationCommands;

    @MockitoBean
    UnlockQuery unlockQuery;

    @MockitoBean
    SessionClosingHandler closingHandler;

    long userId;

    @BeforeEach
    void setUp() {
        userId = newUser();
        adult();
        given(avatarQuery.find(1L)).willReturn(Optional.of(AVATAR));
        given(mateQuery.find(1L)).willReturn(Optional.of(CHAERIN_MATE));
        given(mateQuery.find(2L)).willReturn(Optional.of(COMING_SOON));
        given(mateQuery.activeMates()).willReturn(List.of(CHAERIN_MATE, COMING_SOON));
        given(characterQuery.find(1L)).willReturn(Optional.of(CHAERIN));
        given(relationQuery.intimacyOf(anyLong(), anyLong(), anyLong())).willReturn(SETTLEMENT);
        given(relationQuery.stage(anyLong(), anyLong())).willReturn(2);
        given(relationCommands.settleIntimacy(anyLong(), anyLong(), anyLong(), any(), anyInt(), anyInt()))
                .willReturn(SETTLEMENT);
        given(unlockQuery.between(1L, 1, 2)).willReturn(List.of(SHY));
        // 대화 마무리 초안(LLM)은 DB 트랜잭션 밖에서 만들고, 저장 쪽에 그대로 넘긴다 (개발 서버 풀 2)
        given(closingHandler.draft(any())).willAnswer(inv -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                    .isFalse();
            return DRAFT;
        });
        given(closingHandler.close(any(), any())).willReturn(CLOSING_LINE);
        given(reactionQuery.pick(anyLong(), anyString(), any())).willReturn(Reaction.NONE);
        given(backgroundQuery.current(anyLong(), any())).willCallRealMethod();
    }

    void adult() {
        given(userQuery.find(anyLong()))
                .willAnswer(inv -> Optional.of(new UserProfile(
                        inv.<Long>getArgument(0), "승희", null, 1L, AVATAR.thumbnailUrl(), false, null, null, false)));
    }

    void minor() {
        given(userQuery.find(anyLong()))
                .willAnswer(inv -> Optional.of(new UserProfile(
                        inv.<Long>getArgument(0), "승희", null, 1L, AVATAR.thumbnailUrl(), false, null, null, true)));
    }

    // ---- 16 연결 시작

    @Test
    void startCreatesConnectionWithSceneAndGreeting() throws Exception {
        given(reactionQuery.pick(1L, "GREETING", null)).willReturn(SMILE);

        long first = ((Number) JsonPath.read(
                        startRequest(userId, 1)
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.data.connectionId").isNumber())
                                .andExpect(jsonPath("$.data.mate.name").value("채린"))
                                .andExpect(jsonPath("$.data.mate.format").value("CUBISM"))
                                .andExpect(jsonPath("$.data.mate.modelJsonUrl").value(CHAERIN_MODEL.modelJsonUrl()))
                                .andExpect(jsonPath("$.data.avatar.profileImageUrl")
                                        .value(AVATAR.thumbnailUrl()))
                                .andExpect(jsonPath("$.data.me.nickname").value("승희"))
                                .andExpect(jsonPath("$.data.serverTime").isString())
                                .andExpect(jsonPath("$.data.firstMeet").value(true))
                                .andExpect(jsonPath("$.data.greeting.trigger").value("GREETING"))
                                .andExpect(
                                        jsonPath("$.data.greeting.expression").value("웃음"))
                                .andExpect(jsonPath("$.data.greeting.line").value(nullValue()))
                                .andExpect(
                                        jsonPath("$.data.minorRemainingSeconds").value(nullValue()))
                                .andExpect(jsonPath("$.data.relation.stage").value(2))
                                .andExpect(jsonPath("$.data.relation.stageName").value("아는 사이"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "$.data.connectionId"))
                .longValue();
        verify(relationCommands).meet(userId, 1L);

        end(first, 0, 0, 0, 0, null).andExpect(status().isOk());
        // 같은 메이트와 두 번째 연결은 첫 만남이 아니다
        startRequest(userId, 1).andExpect(jsonPath("$.data.firstMeet").value(false));
    }

    @Test
    void startReturnsHonorificAndSettings() throws Exception {
        given(userQuery.find(anyLong()))
                .willAnswer(inv -> Optional.of(new UserProfile(
                        inv.<Long>getArgument(0), "승희", null, 1L, AVATAR.thumbnailUrl(), false, "승희야", null, false)));
        // 설정은 실제 user 모듈(29와 같은 값): 바꾼 키만 저장, 나머지는 기본값
        jdbc.update(
                "UPDATE users SET settings = '{\"voiceEnabled\": false, \"defaultMode\": \"ADVANCED\"}' WHERE id = ?",
                userId);
        long id = ((Number) JsonPath.read(
                        startRequest(userId, 1)
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.data.me.nickname").value("승희"))
                                .andExpect(jsonPath("$.data.me.honorific").value("승희야"))
                                .andExpect(jsonPath("$.data.me.profileImageUrl").value(AVATAR.thumbnailUrl()))
                                .andExpect(jsonPath("$.data.settings.talkFrequency")
                                        .value("SOMETIMES"))
                                .andExpect(
                                        jsonPath("$.data.settings.voiceEnabled").value(false))
                                .andExpect(
                                        jsonPath("$.data.settings.voiceVolume").value(70))
                                .andExpect(
                                        jsonPath("$.data.settings.voiceSpeed").value("NORMAL"))
                                .andExpect(jsonPath("$.data.settings.pipBubble").value(true))
                                .andExpect(
                                        jsonPath("$.data.settings.defaultMode").value("ADVANCED"))
                                .andExpect(jsonPath("$.data.settings.pipSize").value("M"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "$.data.connectionId"))
                .longValue();
        // 17 이어하기의 me도 같은 모양 (settings는 17 명세에 없음)
        mvc.perform(post("/api/connections/{id}/resume", id).with(login(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.me.honorific").value("승희야"))
                .andExpect(jsonPath("$.data.settings").doesNotExist());
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
    void startWhileInProgressPointsToExistingConnection() throws Exception {
        long id = start(userId);
        expectError(startRequest(userId, 1), 409, "CONNECTION_ALREADY_IN_PROGRESS")
                .andExpect(jsonPath("$.detail.connectionId").value(id));
    }

    // ---- 청소년 3시간 (16 · 21)

    @Test
    void minorHasThreeHoursPerDay() throws Exception {
        minor();
        // 오늘 이미 2시간 50분 연결했다
        jdbc.update(
                "INSERT INTO connections (user_id, character_id, started_at, ended_at, end_type, connected_seconds)"
                        + " VALUES (?, 1, now() - interval '3 hours', now() - interval '1 minute', 'NORMAL', 10200)",
                userId);
        long id = ((Number) JsonPath.read(
                        startRequest(userId, 1)
                                .andExpect(status().isCreated())
                                .andExpect(
                                        jsonPath("$.data.minorRemainingSeconds").value(600))
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "$.data.connectionId"))
                .longValue();
        backdate(id, 10);
        heartbeat(id, 300, 0, 0, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minorRemainingSeconds").value(300))
                .andExpect(jsonPath("$.data.serverTime").isString());
        // 누적값을 줄여 보내 남은 시간을 늘릴 수 없다
        expectError(heartbeat(id, 100, 0, 0, 0), 400, "INVALID_CONNECTION_TIME");
        end(id, 600, 0, 0, 0, "MINOR_LIMIT")
                .andExpect(jsonPath("$.data.endType").value("MINOR_LIMIT"));

        // 3시간을 다 써서 오늘은 더 연결할 수 없다
        expectError(startRequest(userId, 1), 403, "MINOR_DAILY_LIMIT");
    }

    // ---- 18 상태 기록

    @Test
    void eventsReturnReactionAndAwayIntensity() throws Exception {
        long id = start(userId);
        Instant t0 = startedAt(id);
        given(reactionQuery.pick(1L, "AWAY", 1)).willReturn(POUT);
        given(reactionQuery.pick(1L, "RETURN", null)).willReturn(SMILE);

        postEvent(userId, id, "PRESENT", t0)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.eventId").isNumber())
                .andExpect(jsonPath("$.data.reaction").value(nullValue()));
        postEvent(userId, id, "AWAY", t0.plusSeconds(10))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reaction.trigger").value("AWAY"))
                .andExpect(jsonPath("$.data.reaction.intensity").value(1))
                .andExpect(jsonPath("$.data.reaction.expression").value("삐짐"));
        // 재전송은 200 + 같은 반응
        postEvent(userId, id, "AWAY", t0.plusSeconds(10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reaction.intensity").value(1));
        postEvent(userId, id, "RETURN", t0.plusSeconds(20))
                .andExpect(jsonPath("$.data.reaction.trigger").value("RETURN"))
                .andExpect(jsonPath("$.data.reaction.expression").value("웃음"));
        postEvent(userId, id, "AWAY", t0.plusSeconds(30))
                .andExpect(jsonPath("$.data.reaction.intensity").value(2));
        postEvent(userId, id, "IDLE_TALK", t0.plusSeconds(40))
                .andExpect(jsonPath("$.data.reaction.trigger").value("IDLE"));

        // 옛 이벤트 type은 받지 않는다
        expectError(postEvent(userId, id, "FOCUS", t0), 400, "INVALID_EVENT");
        expectError(postEvent(userId, id, "YAWN", t0), 400, "INVALID_EVENT");
        expectError(postEvent(newUser(), id, "PRESENT", t0), 404, "CONNECTION_NOT_FOUND");

        // 연결 시작보다 이른 시각은 시작 시각으로
        // (같은 PRESENT를 시작 시각에 이미 보내서, 다른 type으로 본다)
        postEvent(userId, id, "RETURN", t0.minusSeconds(5)).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM connection_events WHERE connection_id = ? AND occurred_at < ?",
                        Integer.class,
                        id,
                        java.sql.Timestamp.from(t0)))
                .isZero();
    }

    // ---- 21 heartbeat · 17 이어하기

    @Test
    void heartbeatAnnouncesAStageUpOncePerStage() throws Exception {
        // 연결 중 '새 표정이 열렸어요' (api.md 21 stageUp, SCR-56): 미리보기 단계가 시작 단계보다 높아지면 그 단계에서 한 번만
        long id = start(userId);
        backdate(id, 120);
        given(relationQuery.stage(userId, 1L)).willReturn(1);
        given(relationQuery.previewStage(eq(userId), eq(1L), any(), anyInt())).willReturn(1);
        heartbeat(id, 600, 500, 600, 0).andExpect(jsonPath("$.data.stageUp").value(nullValue()));

        given(relationQuery.previewStage(eq(userId), eq(1L), any(), anyInt())).willReturn(2);
        given(unlockQuery.between(1L, 1, 2)).willReturn(List.of(SHY));
        heartbeat(id, 4000, 3700, 4000, 0)
                .andExpect(jsonPath("$.data.stageUp.stage").value(2))
                .andExpect(jsonPath("$.data.stageUp.stageName").value("아는 사이"))
                .andExpect(jsonPath("$.data.stageUp.unlocked[0].expressionName").value("shy"));
        heartbeat(id, 4100, 3800, 4100, 0).andExpect(jsonPath("$.data.stageUp").value(nullValue())); // 같은 단계는 한 번만
        assertThat(jdbc.queryForObject("SELECT notified_stage FROM connections WHERE id = ?", Integer.class, id))
                .isEqualTo(2);
        // 다음 단계는 이미 알린 단계 다음부터만 (2단계 해금을 다시 보내지 않음)
        given(relationQuery.previewStage(eq(userId), eq(1L), any(), anyInt())).willReturn(3);
        given(unlockQuery.between(1L, 2, 3)).willReturn(List.of(NIGHT_UNLOCK));
        heartbeat(id, 5000, 4700, 5000, 0)
                .andExpect(jsonPath("$.data.stageUp.stage").value(3))
                .andExpect(jsonPath("$.data.stageUp.unlocked.length()").value(1))
                .andExpect(jsonPath("$.data.stageUp.unlocked[0].title").value("밤 인사"));
    }

    static final UnlockInfo NIGHT_UNLOCK = new UnlockInfo(10, "VOICE_LINE", "밤 인사", 3, null, "voice/night.mp3");

    @Test
    void heartbeatStoresTimesAndResumeReturnsThem() throws Exception {
        long id = start(userId);
        backdate(id, 30);

        heartbeat(id, 1800, 1500, 1700, 900).andExpect(status().isOk());
        assertThat(jdbc.queryForMap(
                        "SELECT heartbeat_connected_seconds, heartbeat_present_seconds, heartbeat_camera_on_seconds,"
                                + " heartbeat_pip_seconds FROM connections WHERE id = ?",
                        id))
                .containsEntry("heartbeat_connected_seconds", 1800)
                .containsEntry("heartbeat_present_seconds", 1500)
                .containsEntry("heartbeat_camera_on_seconds", 1700)
                .containsEntry("heartbeat_pip_seconds", 900);

        // 순서가 틀리거나(present > cameraOn) 경과 시간을 넘으면 400, 생존 신호는 갱신
        jdbc.update("UPDATE connections SET last_heartbeat_at = NULL WHERE id = ?", id);
        expectError(heartbeat(id, 1800, 1750, 1700, 0), 400, "INVALID_CONNECTION_TIME");
        expectError(heartbeat(id, 9999, 0, 0, 0), 400, "INVALID_CONNECTION_TIME");
        expectError(heartbeat(id, 100, 0, 0, 200), 400, "INVALID_CONNECTION_TIME");
        assertThat(jdbc.queryForObject(
                        "SELECT last_heartbeat_at IS NOT NULL FROM connections WHERE id = ?", Boolean.class, id))
                .isTrue();

        mvc.perform(post("/api/connections/{id}/resume", id).with(login(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connectionId").value(id))
                .andExpect(jsonPath("$.data.mate.name").value("채린"))
                .andExpect(jsonPath("$.data.connectedSeconds").value(1800))
                .andExpect(jsonPath("$.data.presentSeconds").value(1500))
                .andExpect(jsonPath("$.data.cameraOnSeconds").value(1700))
                .andExpect(jsonPath("$.data.pipSeconds").value(900))
                .andExpect(jsonPath("$.data.studySeconds").doesNotExist());
        // 이어하기는 이벤트를 넣지 않는다 (쉬는 시간 없음)
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM connection_events WHERE connection_id = ?", Integer.class, id))
                .isZero();
    }

    // ---- 배경 (2026-11-23, ADR 0032)

    @Test
    void startAndResumeReturnMateBackgrounds() throws Exception {
        // 배경이 없는 메이트: 빈 목록 · null
        long none = start(userId);
        mvc.perform(post("/api/connections/{id}/resume", none).with(login(userId)))
                .andExpect(jsonPath("$.data.backgrounds").isEmpty())
                .andExpect(jsonPath("$.data.background").value(nullValue()));
        end(none, 0, 0, 0, 0, null).andExpect(status().isOk());

        // 실제 조회(활성 · 정렬)는 content BackgroundQueryTest. 여기서는 목록 → 응답 모양과 지금 배경 고르기
        given(backgroundQuery.backgrounds(1L))
                .willReturn(List.of(
                        new BackgroundInfo("classroom", "교실", "https://cdn.test/bg/classroom.png", "공부할 때", true),
                        new BackgroundInfo("cafe", "카페", "https://cdn.test/bg/cafe.png", null, false)));
        String body = startRequest(userId, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.backgrounds.length()").value(2))
                .andExpect(jsonPath("$.data.backgrounds[0].code").value("classroom"))
                .andExpect(jsonPath("$.data.backgrounds[0].imageUrl").value("https://cdn.test/bg/classroom.png"))
                .andExpect(jsonPath("$.data.backgrounds[0].guide").doesNotExist())
                .andExpect(jsonPath("$.data.background").value("classroom"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.data.connectionId")).longValue();

        // 대화로 바뀐 배경은 이어하기가 그대로 (목록에 없는 code면 기본으로)
        jdbc.update("UPDATE connections SET background_code = 'cafe' WHERE id = ?", id);
        mvc.perform(post("/api/connections/{id}/resume", id).with(login(userId)))
                .andExpect(jsonPath("$.data.background").value("cafe"));
        jdbc.update("UPDATE connections SET background_code = 'old' WHERE id = ?", id);
        mvc.perform(post("/api/connections/{id}/resume", id).with(login(userId)))
                .andExpect(jsonPath("$.data.background").value("classroom"));
    }

    // ---- 22 연결 끊기 · 23 연결 기록

    @Test
    void endStoresTimesOnceAndReportRepeatsIt() throws Exception {
        long id = start(userId);
        Instant t0 = startedAt(id);
        backdate(id, 60);
        t0 = startedAt(id);
        postEvent(userId, id, "PRESENT", t0).andExpect(status().isCreated());
        postEvent(userId, id, "AWAY", t0.plusSeconds(600)).andExpect(status().isCreated());
        postEvent(userId, id, "RETURN", t0.plusSeconds(900)).andExpect(status().isCreated());

        expectError(end(id, 3700, 0, 0, 0, null), 400, "INVALID_CONNECTION_TIME"); // 경과 시간(1시간) 초과
        expectError(end(id, 3000, 2900, 2800, 0, null), 400, "INVALID_CONNECTION_TIME");
        expectError(end(id, 3000, 2000, 2800, 0, "AUTO_CLOSED"), 400, "INVALID_REQUEST"); // 배치만 쓰는 종류

        expectReport(end(id, 3000, 2000, 2800, 600, "AUTO_AWAY").andExpect(status().isOk()), id);
        expectReport(end(id, 10, 10, 10, 0, null).andExpect(status().isOk()), id); // 다시 불러도 같은 결과
        expectReport(mvc.perform(get("/api/connections/{id}", id).with(login(userId))), id);

        verify(relationCommands, times(1)).settleIntimacy(anyLong(), anyLong(), anyLong(), any(), anyInt(), anyInt());
        verify(relationCommands).settleIntimacy(eq(userId), eq(1L), eq(id), any(), eq(2000), eq(3000));
        verify(relationCommands, times(1)).recordUnlocks(userId, List.of(9L));
        SessionClosing closing = new SessionClosing(id, userId, 1L, "채린", "AUTO_AWAY", 3000, null);
        verify(closingHandler, times(1)).draft(any());
        verify(closingHandler).close(eq(closing), same(DRAFT));
        assertThat(jdbc.queryForMap(
                        "SELECT end_type, connected_seconds, present_seconds, away_seconds, camera_on_seconds,"
                                + " pip_seconds, return_count, closing_line FROM connections WHERE id = ?",
                        id))
                .containsEntry("end_type", "AUTO_AWAY")
                .containsEntry("connected_seconds", 3000)
                .containsEntry("present_seconds", 2000)
                .containsEntry("away_seconds", 300) // AWAY 600초 → RETURN 900초
                .containsEntry("camera_on_seconds", 2800)
                .containsEntry("pip_seconds", 600)
                .containsEntry("return_count", 1)
                .containsEntry("closing_line", CLOSING_LINE);

        expectError(heartbeat(id, 10, 0, 0, 0), 409, "CONNECTION_ENDED");
        expectError(mvc.perform(post("/api/connections/{id}/resume", id).with(login(userId))), 409, "CONNECTION_ENDED");
        expectError(postEvent(userId, id, "PRESENT", Instant.now()), 409, "CONNECTION_ENDED");
    }

    /** 8단계: 초대 코드를 입력한 뒤 처음 끝나는 연결에서 보상, 다음 연결에는 없음 (22 '서버가 하는 일' 7) */
    @Test
    void firstEndAfterReferralGivesRewardOnce() throws Exception {
        long inviter = newUser();
        jdbc.update(
                "INSERT INTO referrals (inviter_id, invitee_id, device_hash) VALUES (?, ?, 'dev-x')", inviter, userId);
        long id = start(userId);
        backdate(id, 60);
        end(id, 600, 600, 600, 0, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referralReward.amount").value(1000));
        mvc.perform(get("/api/connections/{id}", id).with(login(userId)))
                .andExpect(jsonPath("$.data.referralReward.amount").value(1000)); // 다시 읽어도 같은 값
        end(id, 600, 600, 600, 0, null)
                .andExpect(jsonPath("$.data.referralReward.amount").value(1000)); // 22 다시
        assertThat(jdbc.queryForMap(
                        "SELECT qualified_connection_id, inviter_rewarded, invitee_rewarded FROM referrals"
                                + " WHERE invitee_id = ?",
                        userId))
                .containsEntry("qualified_connection_id", id)
                .containsEntry("inviter_rewarded", true)
                .containsEntry("invitee_rewarded", true);
        assertThat(jdbc.queryForList(
                        "SELECT user_id FROM credit_grants WHERE kind = 'INVITE' AND user_id IN (?, ?) ORDER BY user_id",
                        Long.class,
                        inviter,
                        userId))
                .containsExactlyInAnyOrder(inviter, userId);

        long next = start(userId);
        backdate(next, 60);
        end(next, 600, 600, 600, 0, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referralReward").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void reportOfInProgressConnectionIsConflict() throws Exception {
        long id = start(userId);
        expectError(mvc.perform(get("/api/connections/{id}", id).with(login(userId))), 409, "CONNECTION_IN_PROGRESS");
        expectError(mvc.perform(get("/api/connections/{id}", id).with(login(newUser()))), 404, "CONNECTION_NOT_FOUND");
    }

    // ---- 11 홈 · ActiveSessionLookup

    @Test
    void homeShowsMatesLastMateTodayAndActiveConnection() throws Exception {
        mvc.perform(get("/api/home").with(login(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeConnection").value(nullValue()))
                .andExpect(jsonPath("$.data.lastMate").value(nullValue()))
                .andExpect(jsonPath("$.data.todayPresentSeconds").value(0))
                .andExpect(jsonPath("$.data.todayConnectedSeconds").value(0))
                .andExpect(jsonPath("$.data.mates.length()").value(2))
                .andExpect(jsonPath("$.data.mates[0].oneLiner").value(CHAERIN_MATE.oneLiner()))
                .andExpect(jsonPath("$.data.mates[1].comingSoon").value(true));
        assertThat(activeSessionLookup.find(userId)).isEmpty();

        long id = start(userId);
        backdate(id, 10);
        mvc.perform(get("/api/home").with(login(userId)))
                .andExpect(jsonPath("$.data.activeConnection.connectionId").value(id))
                .andExpect(jsonPath("$.data.activeConnection.mateName").value("채린"))
                .andExpect(jsonPath("$.data.lastMate.illustrationUrl").value(CHAERIN_MATE.illustrationUrl()));
        assertThat(activeSessionLookup.find(userId))
                .get()
                .extracting(ActiveSessionSummary::connectionId, ActiveSessionSummary::mateId)
                .containsExactly(id, 1L);

        end(id, 600, 300, 400, 0, null).andExpect(status().isOk());
        mvc.perform(get("/api/home").with(login(userId)))
                .andExpect(jsonPath("$.data.todayPresentSeconds").value(300))
                .andExpect(jsonPath("$.data.todayConnectedSeconds").value(600));
    }

    @Test
    void roomDetailIsGone() throws Exception {
        expectError(mvc.perform(get("/api/rooms/1").with(login(userId))), 404, "NOT_FOUND");
    }

    // ---- 자동 마감 배치

    @Test
    void autoCloseUsesLastHeartbeatTimes() throws Exception {
        long id = start(userId);
        backdate(id, 8 * 60);
        jdbc.update(
                "UPDATE connections SET last_heartbeat_at = started_at + interval '1 hour',"
                        + " heartbeat_connected_seconds = 3600, heartbeat_present_seconds = 3000,"
                        + " heartbeat_camera_on_seconds = 3300, heartbeat_pip_seconds = 100 WHERE id = ?",
                id);
        long fresh = start(newUserAdult());

        service.autoCloseStale();

        assertThat(jdbc.queryForMap(
                        "SELECT end_type, ended_at = last_heartbeat_at AS at_heartbeat, connected_seconds,"
                                + " present_seconds, camera_on_seconds, pip_seconds, closing_line"
                                + " FROM connections WHERE id = ?",
                        id))
                .containsEntry("end_type", "AUTO_CLOSED")
                .containsEntry("at_heartbeat", true)
                .containsEntry("connected_seconds", 3600)
                .containsEntry("present_seconds", 3000)
                .containsEntry("camera_on_seconds", 3300)
                .containsEntry("pip_seconds", 100)
                .containsEntry("closing_line", CLOSING_LINE);
        verify(relationCommands).settleIntimacy(eq(userId), eq(1L), eq(id), any(), eq(3000), eq(3600));
        verify(closingHandler)
                .close(eq(new SessionClosing(id, userId, 1L, "채린", "AUTO_CLOSED", 3600, null)), same(DRAFT));
        assertThat(sessionQuery.find(fresh)).get().matches(SessionInfo::inProgress);
    }

    @Test
    void autoCloseWithoutHeartbeatRecordsZero() throws Exception {
        long id = start(userId);
        backdate(id, 8 * 60);
        jdbc.update("UPDATE connections SET last_heartbeat_at = started_at WHERE id = ?", id);

        service.autoCloseStale();

        assertThat(jdbc.queryForMap("SELECT end_type, connected_seconds FROM connections WHERE id = ?", id))
                .containsEntry("end_type", "AUTO_CLOSED")
                .containsEntry("connected_seconds", 0);
    }

    // ---- 도우미

    long newUser() {
        return jdbc.queryForObject("INSERT INTO users (nickname) VALUES ('연결테스트') RETURNING id", Long.class);
    }

    long newUserAdult() {
        return newUser(); // userQuery 목은 id와 상관없이 같은 프로필을 돌려준다
    }

    /** 연결 시작과 이벤트를 minutes분 앞으로 옮긴다 (경과 시간 검사를 통과하게) */
    void backdate(long id, int minutes) {
        jdbc.update(
                "UPDATE connections SET started_at = started_at - make_interval(mins => ?),"
                        + " last_heartbeat_at = last_heartbeat_at - make_interval(mins => ?) WHERE id = ?",
                minutes,
                minutes,
                id);
        jdbc.update(
                "UPDATE connection_events SET occurred_at = occurred_at - make_interval(mins => ?)"
                        + " WHERE connection_id = ?",
                minutes,
                id);
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

    Instant startedAt(long id) {
        return sessionQuery.find(id).orElseThrow().startedAt();
    }

    ResultActions postEvent(long userId, long id, String type, Instant occurredAt) throws Exception {
        return mvc.perform(post("/api/connections/{id}/events", id)
                .with(login(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"" + type + "\",\"occurredAt\":\"" + occurredAt + "\"}"));
    }

    static String timesJson(int connected, int present, int cameraOn, int pip) {
        return "\"connectedSeconds\":" + connected + ",\"presentSeconds\":" + present + ",\"cameraOnSeconds\":"
                + cameraOn + ",\"pipSeconds\":" + pip;
    }

    ResultActions heartbeat(long id, int connected, int present, int cameraOn, int pip) throws Exception {
        return mvc.perform(post("/api/connections/{id}/heartbeat", id)
                .with(login(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + timesJson(connected, present, cameraOn, pip) + "}"));
    }

    ResultActions end(long id, int connected, int present, int cameraOn, int pip, String endType) throws Exception {
        String type = endType == null ? "" : ",\"endType\":\"" + endType + "\"";
        return mvc.perform(post("/api/connections/{id}/end", id)
                .with(login(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + timesJson(connected, present, cameraOn, pip) + type + "}"));
    }

    static void expectReport(ResultActions result, long id) throws Exception {
        result.andExpect(jsonPath("$.data.connectionId").value(id))
                .andExpect(jsonPath("$.data.startedAt").isString())
                .andExpect(jsonPath("$.data.endedAt").isString())
                .andExpect(jsonPath("$.data.endType").value("AUTO_AWAY"))
                .andExpect(jsonPath("$.data.connectedSeconds").value(3000))
                .andExpect(jsonPath("$.data.presentSeconds").value(2000))
                .andExpect(jsonPath("$.data.awaySeconds").value(300))
                .andExpect(jsonPath("$.data.messageCount").value(0))
                .andExpect(jsonPath("$.data.returnCount").value(1))
                .andExpect(jsonPath("$.data.mate.id").value(1))
                .andExpect(jsonPath("$.data.mate.illustrationUrl").value(CHAERIN_MATE.illustrationUrl()))
                .andExpect(jsonPath("$.data.mate.myRole").value("같은 반 친구"))
                .andExpect(jsonPath("$.data.relation.stageBefore").value(1))
                .andExpect(jsonPath("$.data.relation.stageAfter").value(2))
                .andExpect(jsonPath("$.data.relation.stageUp").value(true))
                .andExpect(jsonPath("$.data.relation.stageName").value("아는 사이"))
                .andExpect(jsonPath("$.data.relation.creditedSeconds").value(2000))
                .andExpect(jsonPath("$.data.relation.nextStageSeconds").value(14000))
                .andExpect(jsonPath("$.data.relation.stageProgress").value(2)) // 2026-11-24 친밀도 막대
                .andExpect(jsonPath("$.data.cameraOnSeconds").isNumber()) // 2026-11-24 '함께한 시간(캠)'
                .andExpect(
                        jsonPath("$.data.relation.unlocked[0].expressionName").value("shy"))
                .andExpect(jsonPath("$.data.relation.pointsAfter").doesNotExist())
                .andExpect(jsonPath("$.data.closingLine").value(CLOSING_LINE))
                .andExpect(jsonPath("$.data.betaFeedbackAvailable").value(true)) // 오늘 평가 전
                .andExpect(jsonPath("$.data.referralReward").value(org.hamcrest.Matchers.nullValue()));
    }

    static ResultActions expectError(ResultActions result, int httpStatus, String code) throws Exception {
        return result.andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.code").value(code));
    }
}
