package com.capsule.session.domain;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "study_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long userId;
    private long roomId;
    private long backgroundId;
    private Integer targetMinutes;
    private Instant startedAt;
    private Instant endedAt;
    private String endType;
    private Instant lastHeartbeatAt;
    private Integer heartbeatStudySeconds;
    private Integer heartbeatPresentSeconds;
    private int focusSeconds;
    private int distractSeconds;
    private int breakSeconds;
    private int returnCount;
    private String closingLine;

    @ElementCollection
    @CollectionTable(name = "session_characters", joinColumns = @JoinColumn(name = "session_id"))
    private List<SessionCharacter> characters = new ArrayList<>();

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

    /** 생존 신호만. 공부 시간 값이 잘못된 heartbeat용 (저장된 값은 그대로 둠) */
    public void heartbeat(Instant now) {
        this.lastHeartbeatAt = now;
    }

    /** heartbeat(api.md 21)가 보낸 그 시점까지의 공부 시간. 자동 마감 때 이 값으로 기록한다 */
    public void heartbeat(Instant now, int studySeconds, int presentSeconds) {
        heartbeat(now);
        this.heartbeatStudySeconds = studySeconds;
        this.heartbeatPresentSeconds = presentSeconds;
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

    @Embeddable
    public record SessionCharacter(long roomId, long characterId) {}
}
