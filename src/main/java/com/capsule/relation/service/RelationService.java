package com.capsule.relation.service;

import static com.capsule.relation.api.RelationTypes.START_POINTS;

import com.capsule.relation.api.RelationCommands;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.api.RelationTypes.AffinityChange;
import com.capsule.relation.api.RelationTypes.ChatScore;
import com.capsule.relation.api.RelationTypes.RelationView;
import com.capsule.relation.api.RelationTypes.SessionEventRef;
import com.capsule.relation.api.RelationTypes.SignalType;
import com.capsule.relation.api.RelationTypes.StudySettlement;
import com.capsule.relation.domain.Relation;
import com.capsule.relation.domain.RelationLog;
import com.capsule.relation.domain.RelationSettlement;
import com.capsule.relation.repository.RelationLogRepository;
import com.capsule.relation.repository.RelationRepository;
import com.capsule.relation.repository.RelationSettlementRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 호감도 최종안 (2026-11-04, ADR 0022).
 * 호감도: 50에서 시작, 0~100. 대화 · 자리 비움 · 연속 출석으로만 바뀐다. 기록의 합 + 50 = 지금 점수.
 * 레벨: 자리 지킴 시간. 관계 단계: 호감도 구간.
 */
@Service
@Transactional
class RelationService implements RelationQuery, RelationCommands {

    /** 심한 말 뒤 그날은 오르지 않는다 */
    private static final String SEVERE = SignalType.HATE_OR_SEXUAL.name();

    private final RelationRepository relations;
    private final RelationLogRepository logs;
    private final RelationSettlementRepository settlements;
    private final AffinityPolicy policy;
    private final Clock clock;
    private final EntityManager entityManager;

    RelationService(
            RelationRepository relations,
            RelationLogRepository logs,
            RelationSettlementRepository settlements,
            AffinityPolicy policy,
            Clock clock,
            EntityManager entityManager) {
        this.relations = relations;
        this.logs = logs;
        this.settlements = settlements;
        this.policy = policy;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public RelationView view(long userId, long characterId) {
        Optional<Relation> found = relations.findByUserIdAndCharacterId(userId, characterId);
        int points = found.map(Relation::points).orElse(START_POINTS);
        int study = found.map(Relation::totalStudySeconds).orElse(0);
        int present = found.map(Relation::totalPresentSeconds).orElse(0);
        int level = policy.levelOf(present);
        int stage = policy.stageOf(points);
        String stageName = policy.stageName(stage);
        return new RelationView(
                characterId,
                level,
                stageName,
                points,
                study,
                null,
                stage,
                stageName,
                present,
                policy.nextLevelSeconds(level));
    }

    @Override
    @Transactional(readOnly = true)
    public AffinityChange changeOf(SessionEventRef event) {
        Optional<Relation> found = relations.findByUserIdAndCharacterId(event.userId(), event.characterId());
        if (found.isEmpty()) return change(START_POINTS, 0, 0);
        Relation rel = found.get();
        List<RelationLog> eventLogs =
                new ArrayList<>(logs.findByRelationIdAndSessionEventId(rel.id(), event.sessionEventId()));
        // STREAK은 session_event_id 없이 기록되므로(DB CHECK) 세션으로 찾는다
        if (event.firstFocusOfSession()) {
            logs.findFirstByRelationIdAndSessionIdAndReason(rel.id(), event.sessionId(), "STREAK")
                    .ifPresent(eventLogs::add);
        }
        return change(rel, eventLogs);
    }

    @Override
    @Transactional(readOnly = true)
    public StudySettlement settlementOf(long userId, long characterId, long sessionId) {
        Optional<Relation> found = relations.findByUserIdAndCharacterId(userId, characterId);
        if (found.isEmpty()) return settlement(START_POINTS, START_POINTS, 1, 1);
        Relation rel = found.get();
        Optional<RelationSettlement> settled = settlements.findByRelationIdAndSessionId(rel.id(), sessionId);
        int levelNow = policy.levelOf(rel.totalPresentSeconds());
        int levelBefore = settled.map(RelationSettlement::levelBefore).orElse(levelNow);
        int levelAfter = settled.map(RelationSettlement::levelAfter).orElse(levelNow);
        Long lastId = logs.lastIdBySession(rel.id(), sessionId);
        if (lastId == null) {
            // 이 세션에서 바뀐 게 없으면 앞선 세션까지의 값. 뒤에 다른 세션이 쌓여도 리포트가 처음과 같다(명세 23)
            Long before = logs.lastIdBeforeSession(rel.id(), sessionId);
            int points = START_POINTS + (before == null ? 0 : logs.sumUpTo(rel.id(), before));
            return settlement(points, points, levelBefore, levelAfter);
        }
        int after = START_POINTS + logs.sumUpTo(rel.id(), lastId);
        return settlement(after - logs.sumBySession(rel.id(), sessionId), after, levelBefore, levelAfter);
    }

    @Override
    public void meet(long userId, long characterId) {
        relations.meet(userId, characterId, Instant.now(clock));
    }

    @Override
    public AffinityChange applySessionEvent(SessionEventRef event) {
        Relation rel = lock(event.userId(), event.characterId());
        if (event.eventType().equals("FOCUS") && event.firstFocusOfSession()) streak(rel, event.sessionId());
        if (event.awaySeconds() != null
                && event.awaySeconds() > policy.props.awayMinSeconds()
                && !logs.existsByRelationIdAndSessionEventId(rel.id(), event.sessionEventId())) {
            // TODO(judgment): J-01 자리 비움 감점 (임시: 1분 넘게 비우면 −1, 하루 최대 −3)
            int left = policy.props.awayDailyCap() + logs.eventLossSince(rel.id(), todayStart());
            int delta = Math.max(policy.props.awayPenalty(), -Math.max(0, left));
            record(rel, "SESSION_EVENT", delta, event.sessionId(), event.sessionEventId(), null, null);
        }
        return changeOf(event);
    }

    /**
     * 연속 출석 (듀오링고식): 그날 처음 공부를 시작하면 출석. 어제도 했으면 이어지고, 2일째부터 하루 한 번 점수.
     * 끊기면 다시 1일째 (감점 없음). 같은 날 두 번째 세션부터는 아무것도 하지 않는다
     */
    // TODO(judgment): J-01 연속 출석 점수 (임시: 2일째부터 하루 +1)
    private void streak(Relation rel, long sessionId) {
        LocalDate today = LocalDate.now(clock.withZone(AffinityPolicy.DAY_ZONE));
        if (today.equals(rel.lastStudyDate())) return;
        int days = today.minusDays(1).equals(rel.lastStudyDate()) ? rel.streakDays() + 1 : 1;
        relations.updateStreak(rel.id(), days, today);
        entityManager.refresh(rel);
        if (days >= 2) record(rel, "STREAK", policy.props.streakPoints(), sessionId, null, null, null);
    }

    @Override
    @Deprecated(forRemoval = true)
    public AffinityChange applyChat(long userId, long characterId, long sessionId, UUID requestId, int delta) {
        return applyChatScore(
                userId,
                characterId,
                sessionId,
                requestId,
                new ChatScore(SignalType.NEUTRAL, Math.clamp(delta, -5, 5), 0));
    }

    @Override
    public AffinityChange applyChatScore(
            long userId, long characterId, long sessionId, UUID requestId, ChatScore score) {
        Relation rel = lock(userId, characterId);
        Optional<RelationLog> done = logs.findByRelationIdAndRequestId(rel.id(), requestId);
        if (done.isPresent()) return change(rel, List.of(done.get()));
        int delta = AffinityPolicy.chatDelta(rel.points(), score);
        if (delta > 0) {
            Instant today = todayStart();
            // TODO(judgment): J-06 오르는 쪽 하루 상한 (임시 +15), 심한 말 뒤 그날 오름 멈춤
            delta = logs.existsByRelationIdAndSignalAndCreatedAtGreaterThanEqual(rel.id(), SEVERE, today)
                    ? 0
                    : Math.min(
                            delta, Math.max(0, policy.props.chatDailyGainCap() - logs.chatGainSince(rel.id(), today)));
        }
        return change(
                rel,
                record(
                        rel,
                        "CHAT",
                        delta,
                        sessionId,
                        null,
                        requestId,
                        score.signal().name()));
    }

    @Override
    @Deprecated(forRemoval = true)
    public StudySettlement settleStudyTime(long userId, long characterId, long sessionId, int studySeconds) {
        return settleStudy(userId, characterId, sessionId, studySeconds, studySeconds);
    }

    @Override
    public StudySettlement settleStudy(
            long userId, long characterId, long sessionId, int studySeconds, int presentSeconds) {
        Relation rel = lock(userId, characterId);
        if (settlements.findByRelationIdAndSessionId(rel.id(), sessionId).isEmpty()) {
            int before = policy.levelOf(rel.totalPresentSeconds());
            int after = policy.levelOf(rel.totalPresentSeconds() + presentSeconds);
            // ponytail: last_met_at = 정산 시각. 자동 마감이면 실제 종료(마지막 heartbeat)보다 6시간 넘게 늦다.
            //   이 값을 화면에 쓰게 되면 계약에 종료 시각을 받아서 넣을 것 (지금 읽는 API 없음)
            relations.addStudy(rel.id(), studySeconds, presentSeconds, after, Instant.now(clock));
            settlements.save(
                    new RelationSettlement(rel.id(), sessionId, presentSeconds, before, after, Instant.now(clock)));
            entityManager.refresh(rel);
        }
        return settlementOf(userId, characterId, sessionId);
    }

    /** 관계가 없으면 만들고 행을 잠근다 */
    private Relation lock(long userId, long characterId) {
        relations.insertIfAbsent(userId, characterId, Instant.now(clock));
        return relations.findLockedByUserIdAndCharacterId(userId, characterId);
    }

    private Instant todayStart() {
        return LocalDate.now(clock.withZone(AffinityPolicy.DAY_ZONE))
                .atStartOfDay(AffinityPolicy.DAY_ZONE)
                .toInstant();
    }

    /**
     * 기록 + 점수 반영 (rel도 갱신됨). 남긴 기록을 돌려준다 (없으면 빈 목록).
     * 0~100 밖으로 나가는 만큼은 자르고 실제로 바뀐 만큼만 기록한다: 그래서 기록의 합 + 50 = 지금 점수가 늘 맞는다.
     * 잘려서 0이면 기록하지 않는다 (DB delta <> 0).
     * ponytail: 0이라 기록이 없으면 같은 이벤트·요청을 다시 받았을 때 그때 점수로 다시 반영될 수 있다(재전송은 몇 초 안이라 거의 같음).
     */
    private List<RelationLog> record(
            Relation rel,
            String reason,
            int delta,
            Long sessionId,
            Long sessionEventId,
            UUID requestId,
            String signal) {
        int applied = AffinityPolicy.clamp(rel.points() + delta) - rel.points();
        if (applied == 0) return List.of();
        RelationLog log = logs.save(new RelationLog(
                rel.id(), reason, applied, sessionId, sessionEventId, requestId, signal, Instant.now(clock)));
        relations.addPoints(rel.id(), applied);
        entityManager.refresh(rel); // 이 행만 다시 읽는다 (컨텍스트 전체를 비우면 다른 모듈 엔티티의 변경이 사라짐)
        return List.of(log);
    }

    private AffinityChange change(Relation rel, List<RelationLog> changed) {
        return change(
                rel.points(), changed.stream().mapToInt(RelationLog::delta).sum(), rel.totalPresentSeconds());
    }

    private AffinityChange change(int points, int delta, int totalPresentSeconds) {
        int stage = policy.stageOf(points);
        return new AffinityChange(
                delta, points, policy.levelOf(totalPresentSeconds), false, stage, policy.stageName(stage));
    }

    private StudySettlement settlement(int before, int after, int levelBefore, int levelAfter) {
        int stage = policy.stageOf(after);
        String stageName = policy.stageName(stage);
        return new StudySettlement(
                levelBefore,
                levelAfter,
                stageName,
                before,
                after,
                null,
                levelAfter > levelBefore,
                stage,
                stageName,
                policy.nextLevelSeconds(levelAfter));
    }
}
