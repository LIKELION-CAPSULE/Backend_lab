package com.capsule.session.internal.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Set;

@Entity
@Table(name = "session_events")
public class SessionEvent {

    public static final String FOCUS = "FOCUS";
    public static final String AWAY = "AWAY";
    public static final String RETURN = "RETURN";
    public static final String BREAK = "BREAK";
    public static final Set<String> TYPES = Set.of(FOCUS, AWAY, RETURN, BREAK);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    long sessionId;
    String type;
    boolean falsePositive;
    Instant occurredAt;

    protected SessionEvent() {}

    public SessionEvent(long sessionId, String type, Instant occurredAt) {
        this.sessionId = sessionId;
        this.type = type;
        this.occurredAt = occurredAt;
    }

    /** '아니야' 버튼 (오탐이면 집중으로 계산) */
    public void markFalsePositive() {
        this.falsePositive = true;
    }

    public Long id() {
        return id;
    }

    public String type() {
        return type;
    }

    public boolean falsePositive() {
        return falsePositive;
    }

    public Instant occurredAt() {
        return occurredAt;
    }
}
