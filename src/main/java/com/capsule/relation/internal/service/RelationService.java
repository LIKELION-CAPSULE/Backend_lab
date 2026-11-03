package com.capsule.relation.internal.service;

import static com.capsule.relation.internal.service.AffinityPolicy.levelName;
import static com.capsule.relation.internal.service.AffinityPolicy.levelOf;
import static com.capsule.relation.internal.service.AffinityPolicy.nextLevelPoints;

import com.capsule.relation.api.RelationCommands;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.api.RelationTypes.AffinityChange;
import com.capsule.relation.api.RelationTypes.RelationView;
import com.capsule.relation.api.RelationTypes.SessionEventRef;
import com.capsule.relation.api.RelationTypes.StudySettlement;
import com.capsule.relation.internal.domain.Relation;
import com.capsule.relation.internal.domain.RelationLog;
import com.capsule.relation.internal.repository.RelationLogRepository;
import com.capsule.relation.internal.repository.RelationRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class RelationService implements RelationQuery, RelationCommands {

    private final RelationRepository relations;
    private final RelationLogRepository logs;
    private final Clock clock;
    private final EntityManager entityManager;

    RelationService(
            RelationRepository relations, RelationLogRepository logs, Clock clock, EntityManager entityManager) {
        this.relations = relations;
        this.logs = logs;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public RelationView view(long userId, long characterId) {
        return relations
                .findByUserIdAndCharacterId(userId, characterId)
                .map(r -> new RelationView(
                        characterId,
                        r.level(),
                        levelName(r.level()),
                        r.points(),
                        r.totalStudySeconds(),
                        nextLevelPoints(r.level())))
                .orElseGet(() -> new RelationView(characterId, 1, levelName(1), 0, 0, nextLevelPoints(1)));
    }

    @Override
    @Transactional(readOnly = true)
    public AffinityChange changeOf(SessionEventRef event) {
        Optional<Relation> found = relations.findByUserIdAndCharacterId(event.userId(), event.characterId());
        if (found.isEmpty()) return new AffinityChange(0, 0, 1, false);
        Relation rel = found.get();
        List<RelationLog> eventLogs =
                new ArrayList<>(logs.findByRelationIdAndSessionEventId(rel.id(), event.sessionEventId()));
        // FIRST_MEET은 session_event_id 없이 기록되므로(DB CHECK) 세션으로 찾는다
        if (event.firstFocusOfSession()) {
            logs.findFirstByRelationIdAndSessionIdAndReason(rel.id(), event.sessionId(), "FIRST_MEET")
                    .ifPresent(eventLogs::add);
        }
        return change(rel, eventLogs);
    }

    @Override
    @Transactional(readOnly = true)
    public StudySettlement settlementOf(long userId, long characterId, long sessionId) {
        Optional<Relation> found = relations.findByUserIdAndCharacterId(userId, characterId);
        if (found.isEmpty()) return settlement(0, 0, 1, 1);
        Relation rel = found.get();
        Long firstId = logs.firstIdBySession(rel.id(), sessionId);
        Long lastId = logs.lastIdBySession(rel.id(), sessionId);
        if (lastId == null) {
            // ponytail: 이 세션 기록이 없으면 현재 값으로 대신한다. 그 뒤 다른 세션 기록이 쌓였으면 세션 당시 값이 아님
            //   (정확히 하려면 세션 종료 시각 이전 기록으로 계산)
            return settlement(rel.points(), rel.points(), rel.level(), rel.level());
        }
        int after = logs.sumUpTo(rel.id(), lastId);
        return settlement(
                after - logs.sumBySession(rel.id(), sessionId),
                after,
                levelOf(logs.peakUpTo(rel.id(), firstId - 1)),
                levelOf(logs.peakUpTo(rel.id(), lastId)));
    }

    @Override
    public void meet(long userId, long characterId) {
        relations.meet(userId, characterId, Instant.now(clock));
    }

    @Override
    public AffinityChange applySessionEvent(SessionEventRef event) {
        Relation rel = lock(event.userId(), event.characterId());
        // FIRST_MEET 존재 확인이 곧 중복 방지: 같은 FOCUS를 다시 받아도 두 번째부터는 기록하지 않는다
        if (event.eventType().equals("FOCUS")
                && event.firstFocusOfSession()
                && !logs.existsByRelationIdAndReason(rel.id(), "FIRST_MEET")) {
            record(rel, "FIRST_MEET", AffinityPolicy.FIRST_MEET_POINTS, event.sessionId(), null, null);
        }
        int delta = AffinityPolicy.sessionEventDelta(event.eventType());
        if (delta != 0 && !logs.existsByRelationIdAndSessionEventId(rel.id(), event.sessionEventId())) {
            record(rel, "SESSION_EVENT", delta, event.sessionId(), event.sessionEventId(), null);
        }
        return changeOf(event);
    }

    @Override
    public AffinityChange applyChat(long userId, long characterId, long sessionId, UUID requestId, int delta) {
        Relation rel = lock(userId, characterId);
        Optional<RelationLog> done = logs.findByRelationIdAndRequestId(rel.id(), requestId);
        if (done.isPresent()) return change(rel, List.of(done.get()));
        int clamped = Math.clamp(delta, -5, 5);
        if (clamped == 0) return change(rel, List.of());
        return change(rel, List.of(record(rel, "CHAT", clamped, sessionId, null, requestId)));
    }

    @Override
    public StudySettlement settleStudyTime(long userId, long characterId, long sessionId, int studySeconds) {
        Relation rel = lock(userId, characterId);
        int delta = AffinityPolicy.studyTimeDelta(studySeconds);
        if (delta != 0) record(rel, "STUDY_TIME", delta, sessionId, null, null);
        relations.addStudySeconds(rel.id(), studySeconds, Instant.now(clock));
        entityManager.refresh(rel);
        return settlementOf(userId, characterId, sessionId);
    }

    /** 관계가 없으면 만들고 행을 잠근다 */
    private Relation lock(long userId, long characterId) {
        relations.insertIfAbsent(userId, characterId, Instant.now(clock));
        return relations.findLockedByUserIdAndCharacterId(userId, characterId);
    }

    /** 기록 + 점수 반영 + 단계 갱신 (rel도 갱신됨). 남긴 기록을 돌려준다 */
    private RelationLog record(
            Relation rel, String reason, int delta, Long sessionId, Long sessionEventId, UUID requestId) {
        RelationLog log = logs.save(
                new RelationLog(rel.id(), reason, delta, sessionId, sessionEventId, requestId, Instant.now(clock)));
        relations.addPoints(rel.id(), delta);
        entityManager.refresh(rel); // 이 행만 다시 읽는다 (컨텍스트 전체를 비우면 다른 모듈 엔티티의 변경이 사라짐)
        rel.raiseLevel(levelOf(rel.points()));
        return log;
    }

    /**
     * 기록들로 바뀐 호감도. levelUp은 "그 기록들 직전까지의 최고점 단계 < 그 기록까지의 최고점 단계"로 정한다.
     * 단계는 내려가지 않으므로 점수가 99↔100을 오가도 이미 오른 단계에서 다시 levelUp이 나오지 않는다.
     */
    private AffinityChange change(Relation rel, List<RelationLog> changed) {
        if (changed.isEmpty()) return new AffinityChange(0, rel.points(), rel.level(), false);
        int delta = changed.stream().mapToInt(RelationLog::delta).sum();
        long firstId = changed.stream().mapToLong(RelationLog::id).min().orElseThrow();
        long lastId = changed.stream().mapToLong(RelationLog::id).max().orElseThrow();
        boolean levelUp = levelOf(logs.peakUpTo(rel.id(), lastId)) > levelOf(logs.peakUpTo(rel.id(), firstId - 1));
        return new AffinityChange(delta, rel.points(), rel.level(), levelUp);
    }

    /** 단계는 그 시점까지의 최고점으로 정한 값 (저장된 level과 같은 규칙) */
    private static StudySettlement settlement(int before, int after, int levelBefore, int levelAfter) {
        return new StudySettlement(
                levelBefore,
                levelAfter,
                levelName(levelAfter),
                before,
                after,
                nextLevelPoints(levelAfter),
                levelAfter > levelBefore);
    }
}
