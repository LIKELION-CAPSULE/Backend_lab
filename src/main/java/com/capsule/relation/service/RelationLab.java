package com.capsule.relation.service;

import com.capsule.common.error.ApiException;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.domain.Relation;
import com.capsule.relation.domain.RelationLog;
import com.capsule.relation.dto.RelationLabView;
import com.capsule.relation.dto.RelationLabView.LogLine;
import com.capsule.relation.repository.RelationLogRepository;
import com.capsule.relation.repository.RelationRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * local 흐름 테스트 전용 (실험실이 켜졌을 때만): 호감도 · 자리 지킴 시간을 손으로 맞추고 변화 기록을 본다.
 * 관계 단계별 말투와 레벨업을 기다리지 않고 바로 보려는 것. 운영에는 이 빈이 없다.
 */
@Service
@ConditionalOnProperty(name = "capsule.llm.lab.enabled", havingValue = "true")
@Transactional
@RequiredArgsConstructor
public class RelationLab {

    private final RelationRepository relations;
    private final RelationLogRepository logs;
    private final RelationQuery query;
    private final AffinityPolicy policy;
    private final Clock clock;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public RelationLabView view(long userId, long characterId) {
        List<LogLine> lines = relations
                .findByUserIdAndCharacterId(userId, characterId)
                .map(r -> logs.findTop30ByRelationIdOrderByIdDesc(r.getId()).stream()
                        .map(l -> new LogLine(
                                l.getReason(), l.getSignal(), l.getDelta(), l.getSessionId(), l.getCreatedAt()))
                        .toList())
                .orElse(List.of());
        return new RelationLabView(query.view(userId, characterId), lines);
    }

    /**
     * null이 아닌 것만 맞춘다.
     * 점수는 맞춘 만큼을 ADJUST로 그 세션 기록에 남겨 "50 + 기록의 합 = 지금 점수"와 리포트 계산(22 · 23)을 지킨다.
     * 세션 없이 남기면, 그 뒤 자기 기록 없이 끝난 세션의 리포트가 앞선 세션 기록까지만 세어 맞춘 점수를 못 본다. 그래서 세션이 필수다.
     * sessionId는 검증하지 않는다(session 모듈 테이블): 화면은 진행 중인 내 세션 id만 보낸다.
     * 자리 지킴 시간은 공부 시간도 같은 만큼 옮겨 공부 시간 ≥ 자리 지킴을 지킨다 (세션 합계와는 달라짐, 테스트 DB 전용).
     */
    public RelationLabView adjust(
            long userId, long characterId, Integer points, Integer presentSeconds, Long sessionId) {
        if (points != null && sessionId == null) throw new ApiException(HttpStatus.BAD_REQUEST, "SESSION_REQUIRED");
        Instant now = Instant.now(clock);
        relations.insertIfAbsent(userId, characterId, now);
        Relation rel = relations.findLockedByUserIdAndCharacterId(userId, characterId);
        if (points != null) {
            int delta = AffinityPolicy.clamp(points) - rel.points();
            if (delta != 0) {
                logs.save(new RelationLog(rel.getId(), "ADJUST", delta, sessionId, null, null, null, now));
                relations.addPoints(rel.getId(), delta);
            }
        }
        if (presentSeconds != null) {
            int target = Math.max(0, presentSeconds);
            int shift = target - rel.getTotalPresentSeconds();
            relations.addStudy(rel.getId(), shift, shift, policy.levelOf(target), now);
        }
        entityManager.refresh(rel); // 벌크 갱신은 컨텍스트에 안 보이므로 이 행만 다시 읽는다 (RelationService와 같음)
        return view(userId, characterId);
    }
}
