package com.capsule.relation.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface RelationLogRepository extends JpaRepository<RelationLog, Long> {

    boolean existsByRelationIdAndReason(long relationId, String reason);

    boolean existsByRelationIdAndSessionEventId(long relationId, long sessionEventId);

    Optional<RelationLog> findByRelationIdAndRequestId(long relationId, UUID requestId);

    @Query("select coalesce(sum(l.delta), 0) from RelationLog l"
            + " where l.relationId = :relationId and l.sessionEventId = :sessionEventId")
    int sumByEvent(long relationId, long sessionEventId);

    @Query("select coalesce(sum(l.delta), 0) from RelationLog l"
            + " where l.relationId = :relationId and l.sessionId = :sessionId and l.reason = :reason")
    int sumBySessionAndReason(long relationId, long sessionId, String reason);

    @Query("select coalesce(sum(l.delta), 0) from RelationLog l"
            + " where l.relationId = :relationId and l.sessionId = :sessionId")
    int sumBySession(long relationId, long sessionId);

    @Query("select max(l.id) from RelationLog l where l.relationId = :relationId and l.sessionId = :sessionId")
    Long lastIdBySession(long relationId, long sessionId);

    @Query("select coalesce(sum(l.delta), 0) from RelationLog l where l.relationId = :relationId and l.id <= :lastId")
    int sumUpTo(long relationId, long lastId);
}
