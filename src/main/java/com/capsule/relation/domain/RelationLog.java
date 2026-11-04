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
    private String signal;
    private Instant createdAt;

    protected RelationLog() {}

    /** @param signal CHAT일 때 말의 종류 (SignalType 이름), 아니면 null */
    public RelationLog(
            long relationId,
            String reason,
            int delta,
            Long sessionId,
            Long sessionEventId,
            UUID requestId,
            String signal,
            Instant createdAt) {
        this.relationId = relationId;
        this.reason = reason;
        this.delta = delta;
        this.sessionId = sessionId;
        this.sessionEventId = sessionEventId;
        this.requestId = requestId;
        this.signal = signal;
        this.createdAt = createdAt;
    }

    public long id() {
        return id;
    }

    public int delta() {
        return delta;
    }

    public String reason() {
        return reason;
    }

    public String signal() {
        return signal;
    }

    public Long sessionId() {
        return sessionId;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
