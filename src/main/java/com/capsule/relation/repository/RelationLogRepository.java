package com.capsule.relation.repository;

import com.capsule.relation.domain.RelationLog;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RelationLogRepository extends JpaRepository<RelationLog, Long> {

    boolean existsByRelationIdAndSessionEventId(long relationId, long sessionEventId);

    Optional<RelationLog> findByRelationIdAndRequestId(long relationId, UUID requestId);

    List<RelationLog> findByRelationIdAndSessionEventId(long relationId, long sessionEventId);

    /** 흐름 테스트 상태판: 최근 변화 기록 */
    List<RelationLog> findTop30ByRelationIdOrderByIdDesc(long relationId);

    Optional<RelationLog> findFirstByRelationIdAndSessionIdAndReason(long relationId, long sessionId, String reason);

    @Query("select coalesce(sum(l.delta), 0) from RelationLog l"
            + " where l.relationId = :relationId and l.sessionId = :sessionId")
    int sumBySession(long relationId, long sessionId);

    /** 이 시각 이후 대화로 오른 점수 합 (내려간 것은 빼지 않음): 하루 상한용 */
    @Query("select coalesce(sum(l.delta), 0) from RelationLog l where l.relationId = :relationId"
            + " and l.reason = 'CHAT' and l.delta > 0 and l.createdAt >= :since")
    int chatGainSince(long relationId, Instant since);

    /** 이 시각 이후 이벤트로 깎인 점수 합 (0 이하). 이벤트 점수는 자리 비움 감점뿐이다: 하루 상한용 */
    @Query("select coalesce(sum(l.delta), 0) from RelationLog l where l.relationId = :relationId"
            + " and l.reason = 'SESSION_EVENT' and l.delta < 0 and l.createdAt >= :since")
    int eventLossSince(long relationId, Instant since);

    @Query("select max(l.id) from RelationLog l where l.relationId = :relationId and l.sessionId = :sessionId")
    Long lastIdBySession(long relationId, long sessionId);

    /** 이 세션보다 앞선 세션의 마지막 기록. 사용자의 세션은 하나씩 차례로 진행되므로(진행 중 세션은 하나) 세션 id 순서 = 시간 순서 */
    @Query("select max(l.id) from RelationLog l where l.relationId = :relationId and l.sessionId < :sessionId")
    Long lastIdBeforeSession(long relationId, long sessionId);

    @Query("select coalesce(sum(l.delta), 0) from RelationLog l where l.relationId = :relationId and l.id <= :lastId")
    int sumUpTo(long relationId, long lastId);
}
