package com.capsule.relation;

import static org.assertj.core.api.Assertions.assertThat;

import com.capsule.TestcontainersConfiguration;
import com.capsule.common.time.ClockConfig;
import com.capsule.relation.api.RelationCommands;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.api.RelationTypes.AffinityChange;
import com.capsule.relation.api.RelationTypes.ChatScore;
import com.capsule.relation.api.RelationTypes.RelationView;
import com.capsule.relation.api.RelationTypes.SessionEventRef;
import com.capsule.relation.api.RelationTypes.SignalType;
import com.capsule.relation.api.RelationTypes.StudySettlement;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 호감도 최종안 (application.yml capsule.relation 값 기준: 하루 +15, 자리 비움 1분 넘게 −1 · 하루 −3, 단계 0/30/70/90) */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RelationServiceTest {

    static final long CHARACTER = 1; // 시드 캐릭터 채린 (룸 1)
    static final ChatScore PLUS_ONE = new ChatScore(SignalType.COMPLIMENT, 1, 0);

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

    SessionEventRef event(long sessionId, String type, boolean firstFocus, Integer awaySeconds) {
        long eventId = jdbc.queryForObject(
                "INSERT INTO session_events (session_id, type) VALUES (?, ?) RETURNING id",
                Long.class,
                sessionId,
                type);
        return new SessionEventRef(userId, CHARACTER, sessionId, eventId, type, firstFocus, awaySeconds);
    }

    AffinityChange chat(long session, ChatScore score) {
        return commands.applyChatScore(userId, CHARACTER, session, UUID.randomUUID(), score);
    }

    int logSum() {
        return jdbc.queryForObject(
                "SELECT coalesce(sum(delta), 0) FROM relation_logs l JOIN user_character_relations r"
                        + " ON r.id = l.relation_id WHERE r.user_id = ?",
                Integer.class,
                userId);
    }

    void studiedYesterday(int streakDays) {
        LocalDate yesterday = LocalDate.now(ClockConfig.KST).minusDays(1);
        jdbc.update(
                "UPDATE user_character_relations SET last_study_date = ?, streak_days = ? WHERE user_id = ?",
                yesterday,
                streakDays,
                userId);
    }

    @Test
    void startsAt50() {
        RelationView unmet = new RelationView(CHARACTER, 1, "친한 사이", 50, 0, null, 2, "친한 사이", 0, 3600);
        assertThat(query.view(userId, CHARACTER)).isEqualTo(unmet);
        commands.meet(userId, CHARACTER);
        assertThat(query.view(userId, CHARACTER)).isEqualTo(unmet);
    }

    @Test
    void chatGainsAreCappedPerDayButLossesAreNot() {
        long session = newSession();
        UUID once = UUID.randomUUID();
        assertThat(commands.applyChatScore(userId, CHARACTER, session, once, PLUS_ONE)
                        .delta())
                .isEqualTo(1);
        assertThat(commands.applyChatScore(userId, CHARACTER, session, once, PLUS_ONE)
                        .points())
                .isEqualTo(51); // 같은 요청은 한 번만
        for (int i = 0; i < 20; i++) chat(session, PLUS_ONE);
        assertThat(query.view(userId, CHARACTER).points()).isEqualTo(65); // 하루 +15까지

        // 내려가는 쪽은 상한 없음
        for (int i = 0; i < 10; i++) chat(session, new ChatScore(SignalType.RUDE, -2, 0));
        assertThat(query.view(userId, CHARACTER).points()).isEqualTo(45);
        assertThat(logSum() + 50).isEqualTo(45); // 기록의 합 + 50 = 지금 점수
    }

    @Test
    void severeWordsCutByPercentAndFreezeGainsForTheDay() {
        long session = newSession();
        ChatScore severe = new ChatScore(SignalType.HATE_OR_SEXUAL, -5, 30);
        AffinityChange cut = chat(session, severe); // 50의 30% = 15
        assertThat(cut.delta()).isEqualTo(-15);
        assertThat(cut.points()).isEqualTo(35);
        assertThat(cut.stage()).isEqualTo(2);
        assertThat(chat(session, PLUS_ONE).delta()).isZero(); // 그날은 더 오르지 않음

        // 낮을 때는 최소 감점 −5
        jdbc.update("UPDATE user_character_relations SET relation_points = 10 WHERE user_id = ?", userId);
        jdbc.update(
                "INSERT INTO relation_logs (relation_id, delta, reason) SELECT id, -25, 'STREAK' FROM"
                        + " user_character_relations WHERE user_id = ?",
                userId); // 합을 맞춰 둔다 (10 = 50 − 15 − 25)
        assertThat(chat(session, severe).points()).isEqualTo(5);
        assertThat(query.view(userId, CHARACTER).stageName()).isEqualTo("아는 사이");
    }

    @Test
    void severeWordsAtZeroStillFreezeGainsForTheDay() {
        // 0점이면 깎인 값이 0이라 기록이 남지 않는다(delta <> 0). 그래도 그날은 오르지 않아야 한다
        long session = newSession();
        commands.meet(userId, CHARACTER);
        jdbc.update("UPDATE user_character_relations SET relation_points = 0 WHERE user_id = ?", userId);
        jdbc.update(
                "INSERT INTO relation_logs (relation_id, delta, reason) SELECT id, -50, 'STREAK' FROM"
                        + " user_character_relations WHERE user_id = ?",
                userId); // 합을 맞춰 둔다 (0 = 50 − 50)
        assertThat(chat(session, new ChatScore(SignalType.HATE_OR_SEXUAL, -5, 30))
                        .delta())
                .isZero();
        assertThat(chat(session, PLUS_ONE).delta()).isZero();
        assertThat(query.view(userId, CHARACTER).points()).isZero();
        assertThat(logSum() + 50).isZero(); // 기록의 합 + 50 = 지금 점수
    }

    @Test
    void awayCostsOnlyWhenLongerThanAMinuteAndAtMost3ADay() {
        long session = newSession();
        assertThat(commands.applySessionEvent(event(session, "AWAY", false, null))
                        .delta())
                .isZero();
        assertThat(commands.applySessionEvent(event(session, "RETURN", false, 30))
                        .delta())
                .isZero(); // 1분 안
        SessionEventRef back = event(session, "RETURN", false, 61);
        assertThat(commands.applySessionEvent(back)).isEqualTo(new AffinityChange(-1, 49, 1, false, 2, "친한 사이"));
        assertThat(query.changeOf(back).delta()).isEqualTo(-1); // 다시 읽어도 같은 변화
        assertThat(commands.applySessionEvent(event(session, "BREAK", false, 300))
                        .delta())
                .isEqualTo(-1);
        assertThat(commands.applySessionEvent(event(session, "FOCUS", false, 300))
                        .delta())
                .isEqualTo(-1);
        assertThat(commands.applySessionEvent(event(session, "RETURN", false, 300))
                        .delta())
                .isZero(); // 하루 −3까지
        assertThat(query.view(userId, CHARACTER).points()).isEqualTo(47);
    }

    @Test
    void streakFromTheSecondDayOncePerDay() {
        long first = newSession();
        assertThat(commands.applySessionEvent(event(first, "FOCUS", true, null)).delta())
                .isZero(); // 1일째

        studiedYesterday(1);
        long today = newSession();
        SessionEventRef focus = event(today, "FOCUS", true, null);
        assertThat(commands.applySessionEvent(focus).delta()).isEqualTo(1); // 2일째 +1
        assertThat(query.changeOf(focus).delta()).isEqualTo(1);
        assertThat(commands.applySessionEvent(event(newSession(), "FOCUS", true, null))
                        .delta())
                .isZero(); // 같은 날 두 번째 세션

        // 끊기면 다시 1일째
        jdbc.update("UPDATE user_character_relations SET last_study_date = current_date - 3 WHERE user_id = ?", userId);
        assertThat(commands.applySessionEvent(event(newSession(), "FOCUS", true, null))
                        .delta())
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT streak_days FROM user_character_relations WHERE user_id = ?", Integer.class, userId))
                .isEqualTo(1);
    }

    @Test
    void levelIsPresentTimeAndSettlementIsStable() {
        long session = newSession();
        chat(session, PLUS_ONE);
        StudySettlement settled = commands.settleStudy(userId, CHARACTER, session, 4000, 3700); // 자리 지킴 61분 → Lv.2
        assertThat(settled).isEqualTo(new StudySettlement(1, 2, "친한 사이", 50, 51, null, true, 2, "친한 사이", 7200));
        assertThat(commands.settleStudy(userId, CHARACTER, session, 4000, 3700)).isEqualTo(settled); // 한 번만

        long next = newSession();
        commands.settleStudy(userId, CHARACTER, next, 4000, 3600); // 뒤 세션이 쌓여도
        assertThat(query.settlementOf(userId, CHARACTER, session)).isEqualTo(settled); // 리포트는 그대로
        RelationView view = query.view(userId, CHARACTER);
        assertThat(view.level()).isEqualTo(3);
        assertThat(view.totalStudySeconds()).isEqualTo(8000);
        assertThat(view.totalPresentSeconds()).isEqualTo(7300);
    }

    @Test
    void stagesFollowAffinityUpAndDown() {
        long session = newSession();
        commands.meet(userId, CHARACTER);
        jdbc.update("UPDATE user_character_relations SET relation_points = 89 WHERE user_id = ?", userId);
        AffinityChange up = chat(session, PLUS_ONE);
        assertThat(up.stage()).isEqualTo(4);
        assertThat(up.stageName()).isEqualTo("Lv.4");
        AffinityChange down = chat(session, new ChatScore(SignalType.RUDE, -2, 0));
        assertThat(down.stage()).isEqualTo(3);
    }
}
