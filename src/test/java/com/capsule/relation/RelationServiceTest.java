package com.capsule.relation;

import static org.assertj.core.api.Assertions.assertThat;

import com.capsule.TestcontainersConfiguration;
import com.capsule.relation.api.RelationCommands;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.api.RelationTypes.AffinityChange;
import com.capsule.relation.api.RelationTypes.ChatSignal;
import com.capsule.relation.api.RelationTypes.RelationView;
import com.capsule.relation.api.RelationTypes.SessionEventRef;
import com.capsule.relation.api.RelationTypes.StudySettlement;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RelationServiceTest {

    static final long CHARACTER = 1; // 시드 캐릭터 채린 (룸 1)

    @Autowired
    RelationCommands commands;

    @Autowired
    RelationQuery query;

    @Autowired
    JdbcTemplate jdbc;

    long userId;

    @BeforeEach
    void newUser() {
        // relation_logs가 users·study_sessions·session_events를 참조하므로 행을 직접 넣는다
        userId = jdbc.queryForObject(
                "INSERT INTO users (nickname, email, password) VALUES ('테스트', ?, 'x') RETURNING id",
                Long.class,
                "relation-" + UUID.randomUUID() + "@test.invalid");
    }

    long newSession() {
        return jdbc.queryForObject(
                "INSERT INTO study_sessions (user_id, room_id, background_id, ended_at, end_type)"
                        + " VALUES (?, 1, 1, now(), 'NORMAL') RETURNING id",
                Long.class,
                userId);
    }

    SessionEventRef event(long sessionId, String type, boolean firstFocus) {
        long eventId = jdbc.queryForObject(
                "INSERT INTO session_events (session_id, type) VALUES (?, ?) RETURNING id",
                Long.class,
                sessionId,
                type);
        return new SessionEventRef(userId, CHARACTER, sessionId, eventId, type, firstFocus);
    }

    int logCount() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM relation_logs l JOIN user_character_relations r ON r.id = l.relation_id"
                        + " WHERE r.user_id = ?",
                Integer.class,
                userId);
    }

    @Test
    void meetThenView() {
        RelationView unmet = new RelationView(CHARACTER, 1, "아는 사이", 0, 0, 30);
        assertThat(query.view(userId, CHARACTER)).isEqualTo(unmet);

        commands.meet(userId, CHARACTER);
        commands.meet(userId, CHARACTER);

        assertThat(query.view(userId, CHARACTER)).isEqualTo(unmet);
    }

    @Test
    void firstMeetBonusOnlyOnce() {
        commands.meet(userId, CHARACTER);
        SessionEventRef first = event(newSession(), "FOCUS", true);

        assertThat(commands.applySessionEvent(first)).isEqualTo(new AffinityChange(5, 5, 1, false));
        assertThat(commands.applySessionEvent(first)).isEqualTo(new AffinityChange(5, 5, 1, false));

        SessionEventRef nextSession = event(newSession(), "FOCUS", true);
        assertThat(commands.applySessionEvent(nextSession)).isEqualTo(new AffinityChange(0, 5, 1, false));
        assertThat(logCount()).isEqualTo(1);
    }

    @Test
    void awayReturnBreak() {
        commands.settleStudyTime(userId, CHARACTER, newSession(), 6000); // 10분당 +1 → 10
        long session = newSession();
        SessionEventRef away = event(session, "AWAY", false);

        assertThat(commands.applySessionEvent(away)).isEqualTo(new AffinityChange(-1, 9, 1, false));
        assertThat(commands.applySessionEvent(event(session, "RETURN", false)))
                .isEqualTo(new AffinityChange(1, 10, 1, false));
        assertThat(commands.applySessionEvent(event(session, "BREAK", false)))
                .isEqualTo(new AffinityChange(0, 10, 1, false));

        // 같은 이벤트를 다시 반영하거나 다시 읽어도 처음 반영한 변화량만 보인다
        assertThat(commands.applySessionEvent(away)).isEqualTo(new AffinityChange(-1, 10, 1, false));
        assertThat(query.changeOf(away)).isEqualTo(new AffinityChange(-1, 10, 1, false));
        assertThat(logCount()).isEqualTo(3);
    }

    @Test
    void pointsStayBetween0And100AndOnlyTheRealChangeIsRecorded() {
        long session = newSession();
        // 0에서 −1: 바뀐 게 없으므로 기록도 없다
        assertThat(commands.applySessionEvent(event(session, "AWAY", false)))
                .isEqualTo(new AffinityChange(0, 0, 1, false));
        assertThat(logCount()).isZero();

        StudySettlement full = commands.settleStudyTime(userId, CHARACTER, session, 66000); // +110 → 100에서 멈춤
        assertThat(full.pointsAfter()).isEqualTo(100);
        assertThat(commands.applySessionEvent(event(newSession(), "RETURN", false)))
                .isEqualTo(new AffinityChange(0, 100, 3, false));
        // 기록의 합 = 지금 점수
        assertThat(jdbc.queryForObject(
                        "SELECT sum(delta) FROM relation_logs l JOIN user_character_relations r ON r.id = l.relation_id"
                                + " WHERE r.user_id = ?",
                        Integer.class,
                        userId))
                .isEqualTo(100);
    }

    @Test
    void chatSignalIsScoredByRelationWithADailyGainCap() {
        long session = newSession();
        UUID praise = UUID.randomUUID();

        assertThat(commands.applyChatSignal(userId, CHARACTER, session, praise, ChatSignal.PRAISE))
                .isEqualTo(new AffinityChange(2, 2, 1, false));
        assertThat(commands.applyChatSignal(userId, CHARACTER, session, praise, ChatSignal.PRAISE))
                .isEqualTo(new AffinityChange(2, 2, 1, false)); // 같은 요청은 한 번만
        assertThat(commands.applyChatSignal(userId, CHARACTER, session, UUID.randomUUID(), ChatSignal.STUDY_WILL))
                .isEqualTo(new AffinityChange(2, 4, 1, false));
        // 하루에 대화로 오를 수 있는 건 5까지: 남은 1만
        assertThat(commands.applyChatSignal(userId, CHARACTER, session, UUID.randomUUID(), ChatSignal.PRAISE))
                .isEqualTo(new AffinityChange(1, 5, 1, false));
        assertThat(commands.applyChatSignal(userId, CHARACTER, session, UUID.randomUUID(), ChatSignal.PRAISE))
                .isEqualTo(new AffinityChange(0, 5, 1, false));
        // 내려가는 쪽은 상한 없음, 털어놓는 말은 감점하지 않음
        assertThat(commands.applyChatSignal(userId, CHARACTER, session, UUID.randomUUID(), ChatSignal.RUDE))
                .isEqualTo(new AffinityChange(-2, 3, 1, false));
        assertThat(commands.applyChatSignal(userId, CHARACTER, session, UUID.randomUUID(), ChatSignal.STRUGGLE))
                .isEqualTo(new AffinityChange(0, 3, 1, false));
        assertThat(logCount()).isEqualTo(4);
        assertThat(query.chatPoints(ChatSignal.ABUSE)).isEqualTo(-5);
    }

    @Test
    void settleStudyTimeMatchesSettlementOf() {
        commands.meet(userId, CHARACTER);
        long session = newSession();
        commands.applySessionEvent(event(session, "FOCUS", true));

        StudySettlement settled = commands.settleStudyTime(userId, CHARACTER, session, 3300); // +5 (10분당 1)

        assertThat(settled).isEqualTo(new StudySettlement(1, 1, "아는 사이", 0, 10, 30, false));
        assertThat(query.settlementOf(userId, CHARACTER, session)).isEqualTo(settled);
        assertThat(query.view(userId, CHARACTER)).isEqualTo(new RelationView(CHARACTER, 1, "아는 사이", 10, 3300, 30));
    }

    @Test
    void levelUpPast30AndNeverDown() {
        long first = newSession();
        commands.settleStudyTime(userId, CHARACTER, first, 17400); // +29

        long second = newSession();
        SessionEventRef back = event(second, "RETURN", false);
        assertThat(commands.applySessionEvent(back)).isEqualTo(new AffinityChange(1, 30, 2, true));
        assertThat(query.changeOf(back).levelUp()).isTrue();

        StudySettlement settled = commands.settleStudyTime(userId, CHARACTER, second, 59); // 10분 미만은 기록 없음
        assertThat(settled).isEqualTo(new StudySettlement(1, 2, "친한 사이", 29, 30, 70, true));
        assertThat(query.settlementOf(userId, CHARACTER, first))
                .isEqualTo(new StudySettlement(1, 1, "아는 사이", 0, 29, 30, false));

        // 점수가 기준 아래로 내려가도 저장된 단계는 유지
        commands.applySessionEvent(event(second, "AWAY", false));
        assertThat(query.view(userId, CHARACTER)).isEqualTo(new RelationView(CHARACTER, 2, "친한 사이", 29, 17459, 70));
    }

    @Test
    void settlementOfQuietSessionDoesNotChangeLater() {
        commands.settleStudyTime(userId, CHARACTER, newSession(), 6000); // +10
        long quiet = newSession();
        StudySettlement atEnd = commands.settleStudyTime(userId, CHARACTER, quiet, 30); // 10분 미만: 기록 없음
        assertThat(atEnd).isEqualTo(new StudySettlement(1, 1, "아는 사이", 10, 10, 30, false));

        commands.settleStudyTime(userId, CHARACTER, newSession(), 12000); // 뒤 세션 +20 → Lv.2
        assertThat(query.settlementOf(userId, CHARACTER, quiet)).isEqualTo(atEnd);
    }

    @Test
    void oscillatingAround30LevelsUpOnce() {
        commands.settleStudyTime(userId, CHARACTER, newSession(), 17400); // +29

        long session = newSession();
        SessionEventRef up = event(session, "RETURN", false); // 30
        SessionEventRef down = event(session, "AWAY", false); // 29
        SessionEventRef upAgain = event(session, "RETURN", false); // 30
        assertThat(commands.applySessionEvent(up).levelUp()).isTrue();
        assertThat(commands.applySessionEvent(down)).isEqualTo(new AffinityChange(-1, 29, 2, false));
        assertThat(commands.applySessionEvent(upAgain)).isEqualTo(new AffinityChange(1, 30, 2, false));
        // 나중에 다시 읽어도 그 이벤트 당시 기준
        assertThat(query.changeOf(up).levelUp()).isTrue();
        assertThat(query.changeOf(upAgain).levelUp()).isFalse();

        // 리포트도 같은 규칙: 이 세션에서 29 → 30, 단계 1 → 2. 다음 세션에서 다시 넘어도 단계는 그대로
        assertThat(query.settlementOf(userId, CHARACTER, session))
                .isEqualTo(new StudySettlement(1, 2, "친한 사이", 29, 30, 70, true));
        long next = newSession();
        commands.applySessionEvent(event(next, "AWAY", false)); // 29
        StudySettlement again = commands.settleStudyTime(userId, CHARACTER, next, 600); // 30
        assertThat(again).isEqualTo(new StudySettlement(2, 2, "친한 사이", 30, 30, 70, false));
        assertThat(query.view(userId, CHARACTER).level()).isEqualTo(again.levelAfter());
    }
}
