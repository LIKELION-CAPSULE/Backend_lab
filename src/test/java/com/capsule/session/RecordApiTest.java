package com.capsule.session;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.TestcontainersConfiguration;
import com.capsule.content.api.AvatarQuery;
import com.capsule.content.api.CharacterQuery;
import com.capsule.content.api.ContentTypes.MateInfo;
import com.capsule.content.api.ContentTypes.UnlockInfo;
import com.capsule.content.api.MateQuery;
import com.capsule.content.api.ReactionQuery;
import com.capsule.content.api.UnlockQuery;
import com.capsule.relation.api.RelationCommands;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.api.RelationTypes.IntimacyNow;
import com.capsule.session.api.SessionClosingHandler;
import com.capsule.user.api.UserQuery;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 내 메이트 (api.md 37) · 내 기록 (api.md 38) 통합 테스트. 연결 행은 실제 DB(캐릭터 1 · 2 시드), 다른 모듈 api는 목.
 * 목 구성은 SessionApiTest와 같게 둬서 스프링 컨텍스트를 같이 쓴다
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RecordApiTest {

    static final List<String> STAGES = List.of("처음 본 사이", "아는 사이", "친구", "단짝", "특별한 사이");
    static final MateInfo MATE1 = mate(1, "채린");
    static final MateInfo MATE2 = mate(2, "도윤");
    static final UnlockInfo SHY = new UnlockInfo(9, "EXPRESSION", "수줍은 웃음", 2, "shy", null);
    static final UnlockInfo NIGHT =
            new UnlockInfo(10, "VOICE_LINE", "잘 자", 3, null, "https://cdn.capsule.invalid/chaerin/night.mp3");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

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
    UnlockQuery unlockQuery;

    @MockitoBean
    SessionClosingHandler closingHandler;

    long userId;

    @BeforeEach
    void setUp() {
        userId = newUser();
        given(mateQuery.find(1L)).willReturn(Optional.of(MATE1));
        given(mateQuery.find(2L)).willReturn(Optional.of(MATE2));
        given(relationQuery.stage(anyLong(), eq(1L))).willReturn(2);
        given(relationQuery.stage(anyLong(), eq(2L))).willReturn(4);
        given(relationQuery.intimacyNow(anyLong(), eq(1L))).willReturn(new IntimacyNow(2, 4000, 14000));
        given(relationQuery.intimacyNow(anyLong(), eq(2L))).willReturn(new IntimacyNow(5, 150000, null));
        given(relationQuery.unlockedContentIds(anyLong())).willReturn(Set.of(9L));
        given(unlockQuery.between(1L, 0, Integer.MAX_VALUE)).willReturn(List.of(SHY, NIGHT));
        given(unlockQuery.between(2L, 0, Integer.MAX_VALUE)).willReturn(List.of());
    }

    // ---- 37 내 메이트

    @Test
    void myMatesEmptyWithoutEndedConnections() throws Exception {
        // 진행 중 연결만 있으면 아직 내 메이트가 아니다
        jdbc.update("INSERT INTO connections (user_id, character_id, started_at) VALUES (?, 1, now())", userId);
        myMates().andExpect(status().isOk()).andExpect(jsonPath("$.data.mates", hasSize(0)));
    }

    @Test
    void myMatesSumsEndedConnectionsLatestFirst() throws Exception {
        connection(1, "2026-11-02T10:00:00Z", 3600, 3000);
        long latest1 = connection(1, "2026-11-05T10:00:00Z", 1200, 1000);
        for (int h = 0; h < 6; h++) {
            connection(2, "2026-11-06T0" + h + ":00:00Z", 600, 500);
        }
        jdbc.update("INSERT INTO connections (user_id, character_id, started_at) VALUES (?, 1, now())", userId);
        connectionOf(newUser(), 1, "2026-11-07T10:00:00Z", 9999, 9999); // 남의 연결

        myMates()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mates", hasSize(2)))
                // 마지막 연결이 최근인 메이트(2)부터
                .andExpect(jsonPath("$.data.mates[0].id").value(2))
                .andExpect(jsonPath("$.data.mates[0].connectionCount").value(6))
                .andExpect(jsonPath("$.data.mates[0].totalPresentSeconds").value(3000))
                .andExpect(jsonPath("$.data.mates[0].totalConnectedSeconds").value(3600))
                .andExpect(jsonPath("$.data.mates[0].relation.stage").value(5))
                .andExpect(jsonPath("$.data.mates[0].relation.stageName").value("특별한 사이"))
                .andExpect(jsonPath("$.data.mates[0].relation.nextStageName").value(nullValue()))
                .andExpect(jsonPath("$.data.mates[0].relation.nextStageSeconds").value(nullValue()))
                .andExpect(jsonPath("$.data.mates[0].unlocks", hasSize(0)))
                .andExpect(jsonPath("$.data.mates[0].recent", hasSize(5)))
                .andExpect(jsonPath("$.data.mates[1].id").value(1))
                .andExpect(jsonPath("$.data.mates[1].name").value("채린"))
                .andExpect(jsonPath("$.data.mates[1].cardImageUrl").value(MATE1.cardImageUrl()))
                .andExpect(jsonPath("$.data.mates[1].firstMetAt").value("2026-11-02T10:00:00Z"))
                .andExpect(jsonPath("$.data.mates[1].lastConnectedAt").value("2026-11-05T10:00:00Z"))
                .andExpect(jsonPath("$.data.mates[1].connectionCount").value(2))
                .andExpect(jsonPath("$.data.mates[1].totalPresentSeconds").value(4000))
                .andExpect(jsonPath("$.data.mates[1].totalConnectedSeconds").value(4800))
                .andExpect(jsonPath("$.data.mates[1].relation.stage").value(2))
                .andExpect(jsonPath("$.data.mates[1].relation.stageName").value("아는 사이"))
                .andExpect(jsonPath("$.data.mates[1].relation.nextStageName").value("친구"))
                .andExpect(jsonPath("$.data.mates[1].relation.nextStageSeconds").value(14000))
                .andExpect(jsonPath("$.data.mates[1].unlocks[*].id", contains(9, 10)))
                .andExpect(jsonPath("$.data.mates[1].unlocks[0].unlocked").value(true))
                .andExpect(
                        jsonPath("$.data.mates[1].unlocks[0].unlockStageName").value("아는 사이"))
                .andExpect(jsonPath("$.data.mates[1].unlocks[0].expressionName").value("shy"))
                .andExpect(jsonPath("$.data.mates[1].unlocks[1].unlocked").value(false))
                .andExpect(jsonPath("$.data.mates[1].unlocks[1].type").value("VOICE_LINE"))
                .andExpect(
                        jsonPath("$.data.mates[1].unlocks[1].unlockStageName").value("친구"))
                .andExpect(jsonPath("$.data.mates[1].unlocks[1].assetUrl").value(NIGHT.assetUrl()))
                .andExpect(jsonPath("$.data.mates[1].recent", hasSize(2)))
                .andExpect(jsonPath("$.data.mates[1].recent[0].connectionId").value(latest1))
                .andExpect(jsonPath("$.data.mates[1].recent[0].endedAt").value("2026-11-05T11:00:00Z"))
                .andExpect(jsonPath("$.data.mates[1].recent[0].presentSeconds").value(1000))
                .andExpect(
                        jsonPath("$.data.mates[1].recent[0].connectedSeconds").value(1200));
    }

    // ---- 38 내 기록

    @Test
    void recordsEmptyStillHasZeroBuckets() throws Exception {
        records("range=WEEK&date=2026-11-04")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.from").value("2026-11-02"))
                .andExpect(jsonPath("$.data.to").value("2026-11-08"))
                .andExpect(jsonPath("$.data.totalPresentSeconds").value(0))
                .andExpect(jsonPath("$.data.totalConnectedSeconds").value(0))
                .andExpect(jsonPath("$.data.connectedDays").value(0))
                .andExpect(jsonPath("$.data.connectionCount").value(0))
                .andExpect(jsonPath("$.data.buckets", hasSize(7)))
                .andExpect(jsonPath("$.data.buckets[0].key").value("2026-11-02"))
                .andExpect(jsonPath("$.data.buckets[0].presentSeconds").value(0))
                .andExpect(jsonPath("$.data.buckets[6].key").value("2026-11-08"))
                .andExpect(jsonPath("$.data.mates", hasSize(0)))
                .andExpect(jsonPath("$.data.recent", hasSize(0)));
        // 기준 날짜가 없으면 오늘
        records("range=MONTH")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.buckets").isArray());
    }

    @Test
    void weekCountsConnectionsOnTheDayTheyStartedFrom5am() throws Exception {
        // KST 11/2(월) 04:59 = 전날(11/1 일요일) → 이 주에서 빠짐
        connection(1, "2026-11-01T19:59:00Z", 100, 100);
        // KST 11/2(월) 05:00 → 11/2
        long monday = connection(1, "2026-11-01T20:00:00Z", 1000, 800);
        // KST 11/4(수) 03:00 → 11/3
        connection(1, "2026-11-03T18:00:00Z", 2000, 1500);
        // KST 11/9(월) 04:59 → 11/8(일). 다음 날 끝나도 시작한 날에 센다
        long sunday = connection(1, "2026-11-08T19:59:00Z", 3000, 2500);
        // KST 11/9(월) 05:00 → 다음 주
        connection(1, "2026-11-08T20:00:00Z", 100, 100);
        // 진행 중 연결은 빠짐
        jdbc.update(
                "INSERT INTO connections (user_id, character_id, started_at) VALUES (?, 1, '2026-11-04T10:00:00Z')",
                userId);
        connectionOf(newUser(), 1, "2026-11-04T10:00:00Z", 9999, 9999); // 남의 연결

        records("range=WEEK&date=2026-11-04")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.from").value("2026-11-02"))
                .andExpect(jsonPath("$.data.to").value("2026-11-08"))
                .andExpect(jsonPath("$.data.totalConnectedSeconds").value(6000))
                .andExpect(jsonPath("$.data.totalPresentSeconds").value(4800))
                .andExpect(jsonPath("$.data.connectedDays").value(3))
                .andExpect(jsonPath("$.data.connectionCount").value(3))
                .andExpect(jsonPath("$.data.buckets", hasSize(7)))
                .andExpect(jsonPath("$.data.buckets[0].key").value("2026-11-02"))
                .andExpect(jsonPath("$.data.buckets[0].connectedSeconds").value(1000))
                .andExpect(jsonPath("$.data.buckets[0].presentSeconds").value(800))
                .andExpect(jsonPath("$.data.buckets[1].connectedSeconds").value(2000))
                .andExpect(jsonPath("$.data.buckets[2].connectedSeconds").value(0))
                .andExpect(jsonPath("$.data.buckets[6].key").value("2026-11-08"))
                .andExpect(jsonPath("$.data.buckets[6].connectedSeconds").value(3000))
                .andExpect(jsonPath("$.data.mates", hasSize(1)))
                .andExpect(jsonPath("$.data.mates[0].mateId").value(1))
                .andExpect(jsonPath("$.data.mates[0].name").value("채린"))
                .andExpect(jsonPath("$.data.mates[0].connectionCount").value(3))
                .andExpect(jsonPath("$.data.mates[0].presentSeconds").value(4800))
                .andExpect(jsonPath("$.data.mates[0].connectedSeconds").value(6000))
                .andExpect(jsonPath("$.data.mates[0].stage").value(2))
                // 최근 끝난 순
                .andExpect(jsonPath("$.data.recent", hasSize(3)))
                .andExpect(jsonPath("$.data.recent[0].connectionId").value(sunday))
                .andExpect(jsonPath("$.data.recent[0].mateName").value("채린"))
                .andExpect(jsonPath("$.data.recent[2].connectionId").value(monday));
        // 주의 아무 날이나 기준으로 줘도 같은 주 (일요일 기준 → 그 주 월요일부터)
        records("range=WEEK&date=2026-11-08")
                .andExpect(jsonPath("$.data.from").value("2026-11-02"))
                .andExpect(jsonPath("$.data.connectionCount").value(3));
    }

    @Test
    void monthAndYearBuckets() throws Exception {
        connection(1, "2026-10-31T19:59:00Z", 100, 100); // KST 11/1 04:59 → 10/31
        connection(1, "2026-10-31T20:00:00Z", 200, 200); // KST 11/1 05:00 → 11/1
        connection(1, "2026-11-30T19:59:00Z", 300, 300); // KST 12/1 04:59 → 11/30
        connection(1, "2026-12-31T19:59:00Z", 400, 400); // KST 2027/1/1 04:59 → 12/31
        connection(1, "2026-12-31T20:00:00Z", 500, 500); // KST 2027/1/1 05:00 → 내년

        records("range=MONTH&date=2026-11-15")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.from").value("2026-11-01"))
                .andExpect(jsonPath("$.data.to").value("2026-11-30"))
                .andExpect(jsonPath("$.data.buckets", hasSize(30)))
                .andExpect(jsonPath("$.data.buckets[0].key").value("2026-11-01"))
                .andExpect(jsonPath("$.data.buckets[0].connectedSeconds").value(200))
                .andExpect(jsonPath("$.data.buckets[29].key").value("2026-11-30"))
                .andExpect(jsonPath("$.data.buckets[29].connectedSeconds").value(300))
                .andExpect(jsonPath("$.data.connectionCount").value(2))
                .andExpect(jsonPath("$.data.connectedDays").value(2));

        records("range=YEAR&date=2026-11-15")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.from").value("2026-01-01"))
                .andExpect(jsonPath("$.data.to").value("2026-12-31"))
                .andExpect(jsonPath("$.data.buckets", hasSize(12)))
                .andExpect(jsonPath("$.data.buckets[0].key").value("2026-01"))
                .andExpect(jsonPath("$.data.buckets[0].connectedSeconds").value(0))
                .andExpect(jsonPath("$.data.buckets[9].key").value("2026-10"))
                .andExpect(jsonPath("$.data.buckets[9].connectedSeconds").value(100))
                .andExpect(jsonPath("$.data.buckets[10].key").value("2026-11"))
                .andExpect(jsonPath("$.data.buckets[10].connectedSeconds").value(500))
                .andExpect(jsonPath("$.data.buckets[11].key").value("2026-12"))
                .andExpect(jsonPath("$.data.buckets[11].connectedSeconds").value(400))
                .andExpect(jsonPath("$.data.totalConnectedSeconds").value(1000))
                .andExpect(jsonPath("$.data.connectionCount").value(4))
                .andExpect(jsonPath("$.data.connectedDays").value(4));
    }

    @Test
    void mateIdFiltersAndMatesSortByPresentTime() throws Exception {
        connection(1, "2026-11-03T10:00:00Z", 1000, 500);
        connection(2, "2026-11-04T10:00:00Z", 3000, 2000);
        connection(2, "2026-11-04T12:00:00Z", 1000, 1000);

        records("range=WEEK&date=2026-11-04")
                .andExpect(jsonPath("$.data.connectionCount").value(3))
                .andExpect(jsonPath("$.data.connectedDays").value(2))
                .andExpect(jsonPath("$.data.mates[*].mateId", contains(2, 1)))
                .andExpect(jsonPath("$.data.mates[0].stage").value(4))
                .andExpect(jsonPath("$.data.mates[0].connectionCount").value(2))
                .andExpect(jsonPath("$.data.recent[0].mateName").value("도윤"));

        records("range=WEEK&date=2026-11-04&mateId=1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connectionCount").value(1))
                .andExpect(jsonPath("$.data.totalConnectedSeconds").value(1000))
                .andExpect(jsonPath("$.data.mates", hasSize(1)))
                .andExpect(jsonPath("$.data.mates[0].mateId").value(1))
                .andExpect(jsonPath("$.data.recent", hasSize(1)))
                .andExpect(jsonPath("$.data.buckets[2].connectedSeconds").value(0));
    }

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(get("/api/mates/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/records?range=WEEK")).andExpect(status().isUnauthorized());
    }

    @Test
    void recordsRejectsMissingOrInvalidQuery() throws Exception {
        for (String q : List.of("", "range=DAY", "range=week", "range=WEEK&date=2026-13-01", "range=WEEK&mateId=x")) {
            records(q)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
    }

    // ---- 도우미

    static MateInfo mate(long id, String name) {
        return new MateInfo(
                id,
                name,
                null,
                "ALL",
                List.of(),
                null,
                "https://cdn.capsule.invalid/" + id + "/card.png",
                null,
                null,
                null,
                null,
                null,
                STAGES,
                false,
                true);
    }

    long newUser() {
        return jdbc.queryForObject("INSERT INTO users (nickname) VALUES ('기록테스트') RETURNING id", Long.class);
    }

    /** 끝난 연결 하나. 1시간 뒤에 끝남 */
    long connection(long mateId, String startedAt, int connected, int present) {
        return connectionOf(userId, mateId, startedAt, connected, present);
    }

    long connectionOf(long user, long mateId, String startedAt, int connected, int present) {
        Instant start = Instant.parse(startedAt);
        return jdbc.queryForObject(
                "INSERT INTO connections (user_id, character_id, started_at, ended_at, end_type,"
                        + " connected_seconds, present_seconds, camera_on_seconds)"
                        + " VALUES (?, ?, ?, ?, 'NORMAL', ?, ?, ?) RETURNING id",
                Long.class,
                user,
                mateId,
                Timestamp.from(start),
                Timestamp.from(start.plusSeconds(3600)),
                connected,
                present,
                connected);
    }

    ResultActions myMates() throws Exception {
        return mvc.perform(get("/api/mates/me").with(jwt().jwt(j -> j.subject(String.valueOf(userId)))));
    }

    ResultActions records(String query) throws Exception {
        return mvc.perform(get("/api/records?" + query).with(jwt().jwt(j -> j.subject(String.valueOf(userId)))));
    }
}
