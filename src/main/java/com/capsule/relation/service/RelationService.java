package com.capsule.relation.service;

import static com.capsule.relation.service.AffinityPolicy.levelName;
import static com.capsule.relation.service.AffinityPolicy.levelOf;
import static com.capsule.relation.service.AffinityPolicy.nextLevelPoints;

import com.capsule.relation.api.RelationCommands;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.api.RelationTypes.AffinityChange;
import com.capsule.relation.api.RelationTypes.ChatSignal;
import com.capsule.relation.api.RelationTypes.RelationView;
import com.capsule.relation.api.RelationTypes.SessionEventRef;
import com.capsule.relation.api.RelationTypes.StudySettlement;
import com.capsule.relation.domain.Relation;
import com.capsule.relation.domain.RelationLog;
import com.capsule.relation.repository.RelationLogRepository;
import com.capsule.relation.repository.RelationRepository;
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
            // 이 세션에서 바뀐 게 없으면 앞선 세션까지의 값. 뒤에 다른 세션이 쌓여도 리포트가 처음과 같다(명세 23)
            // ponytail: session_id 없는 기록(EVENT 보상)이 생기면 세션 순서로 못 가르므로 시각 기준으로 바꿀 것
            Long before = logs.lastIdBeforeSession(rel.id(), sessionId);
            long upTo = before == null ? 0 : before;
            int points = logs.sumUpTo(rel.id(), upTo);
            int level = levelOf(logs.peakUpTo(rel.id(), upTo));
            return settlement(points, points, level, level);
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
        if (!logs.existsByRelationIdAndSessionEventId(rel.id(), event.sessionEventId())) {
            record(rel, "SESSION_EVENT", delta, event.sessionId(), event.sessionEventId(), null);
        }
        return changeOf(event);
    }

    @Override
    @Deprecated
    public AffinityChange applyChat(long userId, long characterId, long sessionId, UUID requestId, int delta) {
        Relation rel = lock(userId, characterId);
        Optional<RelationLog> done = logs.findByRelationIdAndRequestId(rel.id(), requestId);
        if (done.isPresent()) return change(rel, List.of(done.get()));
        return change(rel, record(rel, "CHAT", Math.clamp(delta, -5, 5), sessionId, null, requestId));
    }

    @Override
    public AffinityChange applyChatSignal(
            long userId, long characterId, long sessionId, UUID requestId, ChatSignal signal) {
        Relation rel = lock(userId, characterId);
        Optional<RelationLog> done = logs.findByRelationIdAndRequestId(rel.id(), requestId);
        if (done.isPresent()) return change(rel, List.of(done.get()));
        int delta = AffinityPolicy.chatPoints(signal);
        if (delta > 0) {
            Instant today = LocalDate.now(clock.withZone(AffinityPolicy.DAY_ZONE))
                    .atStartOfDay(AffinityPolicy.DAY_ZONE)
                    .toInstant();
            int gained = logs.chatGainSince(rel.id(), today);
            delta = Math.min(delta, Math.max(0, AffinityPolicy.CHAT_DAILY_GAIN_CAP - gained));
        }
        return change(rel, record(rel, "CHAT", delta, sessionId, null, requestId));
    }

    @Override
    @Transactional(readOnly = true)
    public int chatPoints(ChatSignal signal) {
        return AffinityPolicy.chatPoints(signal);
    }

    @Override
    public StudySettlement settleStudyTime(long userId, long characterId, long sessionId, int studySeconds) {
        Relation rel = lock(userId, characterId);
        record(rel, "STUDY_TIME", AffinityPolicy.studyTimeDelta(studySeconds), sessionId, null, null);
        // ponytail: last_met_at = 정산 시각. 자동 마감이면 실제 종료(마지막 heartbeat)보다 6시간 넘게 늦다.
        //   이 값을 화면에 쓰게 되면 계약에 종료 시각을 받아서 넣을 것 (지금 읽는 API 없음)
        relations.addStudySeconds(rel.id(), studySeconds, Instant.now(clock));
        entityManager.refresh(rel);
        return settlementOf(userId, characterId, sessionId);
    }

    /** 관계가 없으면 만들고 행을 잠근다 */
    private Relation lock(long userId, long characterId) {
        relations.insertIfAbsent(userId, characterId, Instant.now(clock));
        return relations.findLockedByUserIdAndCharacterId(userId, characterId);
    }

    /**
     * 기록 + 점수 반영 + 단계 갱신 (rel도 갱신됨). 남긴 기록을 돌려준다 (없으면 빈 목록).
     * 0~100 밖으로 나가는 만큼은 자르고 실제로 바뀐 만큼만 기록한다: 그래서 기록의 합 = 지금 점수가 늘 맞는다.
     * 잘려서 0이면 기록하지 않는다 (DB delta <> 0).
     * ponytail: 0이라 기록이 없으면 같은 이벤트·요청을 다시 받았을 때 그때 점수로 다시 반영될 수 있다(재전송은 몇 초 안이라 거의 같음).
     */
    private List<RelationLog> record(
            Relation rel, String reason, int delta, Long sessionId, Long sessionEventId, UUID requestId) {
        int applied = AffinityPolicy.clamp(rel.points() + delta) - rel.points();
        if (applied == 0) return List.of();
        RelationLog log = logs.save(
                new RelationLog(rel.id(), reason, applied, sessionId, sessionEventId, requestId, Instant.now(clock)));
        relations.addPoints(rel.id(), applied);
        entityManager.refresh(rel); // 이 행만 다시 읽는다 (컨텍스트 전체를 비우면 다른 모듈 엔티티의 변경이 사라짐)
        rel.raiseLevel(levelOf(rel.points()));
        return List.of(log);
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
