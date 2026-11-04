package com.capsule.relation.repository;

import com.capsule.relation.domain.RelationSettlement;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RelationSettlementRepository extends JpaRepository<RelationSettlement, RelationSettlement.Key> {

    Optional<RelationSettlement> findByRelationIdAndSessionId(long relationId, long sessionId);
}
