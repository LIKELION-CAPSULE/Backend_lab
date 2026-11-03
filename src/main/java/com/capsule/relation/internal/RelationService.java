package com.capsule.relation.internal;

import static com.capsule.relation.internal.AffinityPolicy.levelName;
import static com.capsule.relation.internal.AffinityPolicy.levelOf;
import static com.capsule.relation.internal.AffinityPolicy.nextLevelPoints;

import com.capsule.relation.api.RelationCommands;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.api.RelationTypes.AffinityChange;
import com.capsule.relation.api.RelationTypes.RelationView;
import com.capsule.relation.api.RelationTypes.SessionEventRef;
import com.capsule.relation.api.RelationTypes.StudySettlement;
import java.time.Clock;
import java.time.Instant;
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

    RelationService(RelationRepository relations, RelationLogRepository logs, Clock clock) {
        this.relations = relations;
        this.logs = logs;
        this.clock = clock;
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
        int delta = logs.sumByEvent(rel.id(), event.sessionEventId());
        // FIRST_MEET은 session_event_id 없이 기록되므로(DB CHECK) 세션으로 찾는다
        if (event.firstFocusOfSession()) delta += logs.sumBySessionAndReason(rel.id(), event.sessionId(), "FIRST_MEET");
        return change(rel, delta);
    }

    @Override
    @Transactional(readOnly = true)
    public StudySettlement settlementOf(long userId, long characterId, long sessionId) {
        Optional<Relation> found = relations.findByUserIdAndCharacterId(userId, characterId);
        if (found.isEmpty()) return settlement(0, 0);
        long relationId = found.get().id();
        Long lastId = logs.lastIdBySession(relationId, sessionId);
        // ponytail: 이 세션 기록이 없으면 현재 점수로 대신한다. 그 뒤 다른 세션 기록이 쌓였으면 세션 당시 점수가 아님
        //   (정확히 하려면 세션 종료 시각 이전 기록의 합)
        int after = lastId == null ? found.get().points() : logs.sumUpTo(relationId, lastId);
        return settlement(after - logs.sumBySession(relationId, sessionId), after);
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
        if (done.isPresent()) return change(rel, done.get().delta());
        int clamped = Math.clamp(delta, -5, 5);
        if (clamped == 0) return change(rel, 0);
        return change(record(rel, "CHAT", clamped, sessionId, null, requestId), clamped);
    }

    @Override
    public StudySettlement settleStudyTime(long userId, long characterId, long sessionId, int studySeconds) {
        Relation rel = lock(userId, characterId);
        int delta = AffinityPolicy.studyTimeDelta(studySeconds);
        if (delta != 0) record(rel, "STUDY_TIME", delta, sessionId, null, null);
        relations.addStudySeconds(rel.id(), studySeconds, Instant.now(clock));
        return settlementOf(userId, characterId, sessionId);
    }

    /** 관계가 없으면 만들고 행을 잠근다 */
    private Relation lock(long userId, long characterId) {
        relations.insertIfAbsent(userId, characterId, Instant.now(clock));
        return relations.findLockedByUserIdAndCharacterId(userId, characterId);
    }

    /** 기록 + 점수 반영 + 단계 갱신. 반영된 관계를 돌려준다 */
    private Relation record(
            Relation rel, String reason, int delta, Long sessionId, Long sessionEventId, UUID requestId) {
        logs.save(new RelationLog(rel.id(), reason, delta, sessionId, sessionEventId, requestId, Instant.now(clock)));
        relations.addPoints(rel.id(), delta);
        Relation fresh = relations.findById(rel.id()).orElseThrow();
        fresh.raiseLevel(levelOf(fresh.points()));
        return fresh;
    }

    private static AffinityChange change(Relation rel, int delta) {
        return new AffinityChange(
                delta, rel.points(), rel.level(), levelOf(rel.points()) > levelOf(rel.points() - delta));
    }

    private static StudySettlement settlement(int before, int after) {
        int levelBefore = levelOf(before);
        int levelAfter = levelOf(after);
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
