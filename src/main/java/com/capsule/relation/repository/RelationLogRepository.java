package com.capsule.relation.repository;

import com.capsule.relation.domain.RelationLog;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RelationLogRepository extends JpaRepository<RelationLog, Long> {

    boolean existsByRelationIdAndReason(long relationId, String reason);

    boolean existsByRelationIdAndSessionEventId(long relationId, long sessionEventId);

    Optional<RelationLog> findByRelationIdAndRequestId(long relationId, UUID requestId);

    List<RelationLog> findByRelationIdAndSessionEventId(long relationId, long sessionEventId);

    Optional<RelationLog> findFirstByRelationIdAndSessionIdAndReason(long relationId, long sessionId, String reason);

    @Query("select coalesce(sum(l.delta), 0) from RelationLog l"
            + " where l.relationId = :relationId and l.sessionId = :sessionId")
    int sumBySession(long relationId, long sessionId);

    @Query("select min(l.id) from RelationLog l where l.relationId = :relationId and l.sessionId = :sessionId")
    Long firstIdBySession(long relationId, long sessionId);

    @Query("select max(l.id) from RelationLog l where l.relationId = :relationId and l.sessionId = :sessionId")
    Long lastIdBySession(long relationId, long sessionId);

    /** 이 세션보다 앞선 세션의 마지막 기록. 사용자의 세션은 하나씩 차례로 진행되므로(진행 중 세션은 하나) 세션 id 순서 = 시간 순서 */
    @Query("select max(l.id) from RelationLog l where l.relationId = :relationId and l.sessionId < :sessionId")
    Long lastIdBeforeSession(long relationId, long sessionId);

    @Query("select coalesce(sum(l.delta), 0) from RelationLog l where l.relationId = :relationId and l.id <= :lastId")
    int sumUpTo(long relationId, long lastId);

    /**
     * 이 기록(포함)까지 점수가 가장 높았던 값 (처음 0점 포함).
     * 단계는 내려가지 않으므로(저장된 level 규칙) 그 시점의 단계 = 이 값의 단계.
     */
    @Query(value = """
            SELECT GREATEST(0, COALESCE(MAX(running), 0)) FROM (
                SELECT SUM(delta) OVER (ORDER BY id) AS running
                FROM relation_logs WHERE relation_id = :relationId AND id <= :lastId) t""", nativeQuery = true)
    int peakUpTo(long relationId, long lastId);
}
