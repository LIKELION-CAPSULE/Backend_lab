package com.capsule.session.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Set;

@Entity
@Table(name = "session_events")
class SessionEvent {

    static final String FOCUS = "FOCUS";
    static final String AWAY = "AWAY";
    static final String RETURN = "RETURN";
    static final String BREAK = "BREAK";
    static final Set<String> TYPES = Set.of(FOCUS, AWAY, RETURN, BREAK);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    long sessionId;
    String type;
    boolean falsePositive;
    Instant occurredAt;

    protected SessionEvent() {}

    SessionEvent(long sessionId, String type, Instant occurredAt) {
        this.sessionId = sessionId;
        this.type = type;
        this.occurredAt = occurredAt;
    }
}
