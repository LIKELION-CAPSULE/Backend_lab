package com.capsule.relation.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "relation_logs")
public class RelationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long relationId;
    private Long sessionId;
    private Long sessionEventId;
    private UUID requestId;
    private int delta;
    private String reason;
    private Instant createdAt;

    protected RelationLog() {}

    public RelationLog(
            long relationId,
            String reason,
            int delta,
            Long sessionId,
            Long sessionEventId,
            UUID requestId,
            Instant createdAt) {
        this.relationId = relationId;
        this.reason = reason;
        this.delta = delta;
        this.sessionId = sessionId;
        this.sessionEventId = sessionEventId;
        this.requestId = requestId;
        this.createdAt = createdAt;
    }

    public long id() {
        return id;
    }

    public int delta() {
        return delta;
    }
}
