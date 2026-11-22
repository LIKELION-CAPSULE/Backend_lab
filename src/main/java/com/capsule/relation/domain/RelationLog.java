package com.capsule.relation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "relation_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RelationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long relationId;
    // 2026-11-23 칸 이름이 connection_id · connection_event_id로 바뀜 (세션 → 연결). 자바 이름은 그대로
    @Column(name = "connection_id")
    private Long sessionId;

    @Column(name = "connection_event_id")
    private Long sessionEventId;

    private UUID requestId;
    private int delta;
    private String reason;
    private String signal;
    private Instant createdAt;

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
}
