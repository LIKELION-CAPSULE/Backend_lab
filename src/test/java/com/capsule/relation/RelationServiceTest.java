package com.capsule.relation;

import static org.assertj.core.api.Assertions.assertThat;

import com.capsule.TestcontainersConfiguration;
import com.capsule.common.time.ClockConfig;
import com.capsule.relation.api.RelationCommands;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.api.RelationTypes.AffinityChange;
import com.capsule.relation.api.RelationTypes.ChatScore;
import com.capsule.relation.api.RelationTypes.IntimacyNow;
import com.capsule.relation.api.RelationTypes.IntimacySettlement;
import com.capsule.relation.api.RelationTypes.RelationView;
import com.capsule.relation.api.RelationTypes.SessionEventRef;
import com.capsule.relation.api.RelationTypes.SignalType;
import com.capsule.relation.api.RelationTypes.StudySettlement;
import com.capsule.relation.service.IntimacyPolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 호감도 최종안 (application.yml capsule.relation 값 기준: 하루 +25, 자리 비움 1분 넘게 −1 · 하루 −3, 단계 0/30/70/90) */
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

    @Autowired
    Clock clock;

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
                "INSERT INTO connections (user_id, character_id, ended_at, end_type)"
                        + " VALUES (?, 1, now(), 'NORMAL') RETURNING id",
                Long.class,
                userId);
    }

    SessionEventRef event(long sessionId, String type, boolean firstFocus, Integer awaySeconds) {
        long eventId = jdbc.queryForObject(
                "INSERT INTO connection_events (connection_id, type) VALUES (?, ?) RETURNING id",
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
        for (int i = 0; i < 30; i++) chat(session, PLUS_ONE);
        assertThat(query.view(userId, CHARACTER).points()).isEqualTo(75); // 하루 +25까지

        // 내려가는 쪽은 상한 없음
        for (int i = 0; i < 10; i++) chat(session, new ChatScore(SignalType.RUDE, -4, 0));
        assertThat(query.view(userId, CHARACTER).points()).isEqualTo(35);
        assertThat(logSum() + 50).isEqualTo(35); // 기록의 합 + 50 = 지금 점수
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

    // ---- 친밀도 (2026-11-23 5단계): 하루 2시간, 대화 없으면 절반, 단계 0 · 1 · 5 · 15 · 40시간

    @Test
    void intimacyCreditsPresentTimeWithDailyCap() {
        assertThat(query.intimacyNow(userId, CHARACTER)).isEqualTo(new IntimacyNow(1, 0, 3600)); // 안 만났으면 1단계
        Instant now = Instant.now(clock);
        long first = newSession();
        chat(first, PLUS_ONE); // 그날 대화 있음 → 절반 아님
        IntimacySettlement s1 = commands.settleIntimacy(userId, CHARACTER, first, now, 4000, 5000);
        assertThat(s1).isEqualTo(new IntimacySettlement(1, 2, 4000, 4000, 14000)); // 1시간 넘어 2단계
        assertThat(s1.stageUp()).isTrue();
        assertThat(commands.settleIntimacy(userId, CHARACTER, first, now, 4000, 5000))
                .isEqualTo(s1); // 같은 연결은 한 번만

        long second = newSession();
        IntimacySettlement s2 = commands.settleIntimacy(userId, CHARACTER, second, now, 5000, 5000);
        assertThat(s2.creditedSeconds()).isEqualTo(3200); // 하루 한도 7200 − 4000
        assertThat(s2.stageUp()).isFalse();
        assertThat(query.intimacyOf(userId, CHARACTER, first)).isEqualTo(s1); // 뒤 연결이 쌓여도 처음 정산 그대로
        assertThat(query.intimacyOf(userId, CHARACTER, second)).isEqualTo(s2);
        assertThat(s2.totalCreditedSeconds()).isEqualTo(7200);
        assertThat(query.stage(userId, CHARACTER)).isEqualTo(2);
        // 내 메이트(37): 지금 단계 · 누적 인정 · 다음 단계(5시간)까지
        assertThat(query.intimacyNow(userId, CHARACTER)).isEqualTo(new IntimacyNow(2, 7200, 10800));
        assertThat(jdbc.queryForObject(
                        "SELECT total_connected_seconds FROM user_character_relations WHERE user_id = ?",
                        Integer.class,
                        userId))
                .isEqualTo(10000);
    }

    @Test
    void intimacyIsHalfWithoutChatThatDay() {
        IntimacySettlement s = commands.settleIntimacy(userId, CHARACTER, newSession(), Instant.now(clock), 3000, 3000);
        assertThat(s).isEqualTo(new IntimacySettlement(1, 1, 1500, 1500, 2100));
        assertThat(query.intimacyOf(userId, CHARACTER, 999_999).creditedSeconds())
                .isZero(); // 정산 없는 연결
    }

    @Test
    void intimacyPolicyStagesAndDay() {
        assertThat(IntimacyPolicy.stageOf(3599)).isEqualTo(1);
        assertThat(IntimacyPolicy.stageOf(18000)).isEqualTo(3);
        assertThat(IntimacyPolicy.stageOf(144000)).isEqualTo(5);
        assertThat(IntimacyPolicy.secondsToNextStage(144000)).isNull();
        // 오전 5시 전은 전날
        assertThat(IntimacyPolicy.dayOf(Instant.parse("2026-11-23T19:59:59Z"))).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(IntimacyPolicy.dayOf(Instant.parse("2026-11-23T20:00:00Z"))).isEqualTo(LocalDate.of(2026, 11, 24));
    }

    @Test
    void unlocksAreRecordedOnce() {
        long content = jdbc.queryForObject(
                "INSERT INTO character_contents (character_id, type, title, expression_name, unlock_stage)"
                        + " VALUES (1, 'EXPRESSION', '수줍은 웃음', 'shy', 2) RETURNING id",
                Long.class);
        commands.recordUnlocks(userId, List.of(content));
        commands.recordUnlocks(userId, List.of(content));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM user_unlocked_contents WHERE user_id = ?", Integer.class, userId))
                .isEqualTo(1);
        assertThat(query.unlockedContentIds(userId)).containsExactly(content); // 내 메이트(37) unlocked
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
