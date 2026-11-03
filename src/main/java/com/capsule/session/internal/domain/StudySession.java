package com.capsule.session.internal.domain;

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
public class StudySession {

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

    public StudySession(long userId, long roomId, long backgroundId, long characterId, int targetMinutes, Instant now) {
        this.userId = userId;
        this.roomId = roomId;
        this.backgroundId = backgroundId;
        this.targetMinutes = targetMinutes;
        this.startedAt = now;
        this.lastHeartbeatAt = now;
        this.characters.add(new SessionCharacter(roomId, characterId));
    }

    public boolean inProgress() {
        return endedAt == null;
    }

    /** 뼈대는 세션당 캐릭터 1명 */
    public long characterId() {
        return characters.getFirst().characterId();
    }

    public void heartbeat(Instant now) {
        this.lastHeartbeatAt = now;
    }

    /** 공부 종료 기록. 완료 대사는 대화 쪽 마무리가 끝난 뒤 {@link #recordClosingLine(String)}로 남긴다 */
    public void close(
            Instant endedAt, String endType, int focusSeconds, int distractSeconds, int breakSeconds, int returnCount) {
        this.endedAt = endedAt;
        this.endType = endType;
        this.focusSeconds = focusSeconds;
        this.distractSeconds = distractSeconds;
        this.breakSeconds = breakSeconds;
        this.returnCount = returnCount;
    }

    public void recordClosingLine(String closingLine) {
        this.closingLine = closingLine;
    }

    public Long id() {
        return id;
    }

    public long userId() {
        return userId;
    }

    public long roomId() {
        return roomId;
    }

    public long backgroundId() {
        return backgroundId;
    }

    public Integer targetMinutes() {
        return targetMinutes;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant endedAt() {
        return endedAt;
    }

    public String endType() {
        return endType;
    }

    public Instant lastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public int focusSeconds() {
        return focusSeconds;
    }

    public int distractSeconds() {
        return distractSeconds;
    }

    public int returnCount() {
        return returnCount;
    }

    public String closingLine() {
        return closingLine;
    }

    @Embeddable
    record SessionCharacter(long roomId, long characterId) {}
}
