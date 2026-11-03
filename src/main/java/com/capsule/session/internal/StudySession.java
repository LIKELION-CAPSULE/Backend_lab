package com.capsule.session.internal;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "study_sessions")
class StudySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    long userId;
    long roomId;
    long backgroundId;
    Integer targetMinutes;
    Instant startedAt;
    Instant endedAt;
    String endType;
    Instant lastHeartbeatAt;
    int focusSeconds;
    int distractSeconds;
    int breakSeconds;
    int returnCount;
    String closingLine;

    @ElementCollection
    @CollectionTable(name = "session_characters", joinColumns = @JoinColumn(name = "session_id"))
    List<SessionCharacter> characters = new ArrayList<>();

    protected StudySession() {}

    StudySession(long userId, long roomId, long backgroundId, long characterId, int targetMinutes, Instant now) {
        this.userId = userId;
        this.roomId = roomId;
        this.backgroundId = backgroundId;
        this.targetMinutes = targetMinutes;
        this.startedAt = now;
        this.lastHeartbeatAt = now;
        this.characters.add(new SessionCharacter(roomId, characterId));
    }

    boolean inProgress() {
        return endedAt == null;
    }

    /** 뼈대는 세션당 캐릭터 1명 */
    long characterId() {
        return characters.getFirst().characterId();
    }

    @Embeddable
    record SessionCharacter(long roomId, long characterId) {}
}
