package com.capsule.relation.repository;

import com.capsule.relation.domain.RelationSettlement;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RelationSettlementRepository extends JpaRepository<RelationSettlement, RelationSettlement.Key> {

    Optional<RelationSettlement> findByRelationIdAndSessionId(long relationId, long sessionId);

    /**
     * 이 정산까지(포함) 인정된 시간 합 = 그 정산 직후 누적. 정산은 관계 행 잠금 안에서 한 줄로 쌓이므로 (시각, 연결 id) 순서가 정산 순서.
     * 뒤 연결이 쌓여도 22 재호출 · 23이 처음 22 응답과 같게 (nextStageSeconds)
     */
    @Query(value = """
            SELECT coalesce(sum(credited_seconds), 0) FROM relation_settlements
            WHERE relation_id = :relationId AND (created_at, connection_id) <= (:createdAt, :connectionId)""", nativeQuery = true)
    int creditedUpTo(long relationId, Instant createdAt, long connectionId);
}
