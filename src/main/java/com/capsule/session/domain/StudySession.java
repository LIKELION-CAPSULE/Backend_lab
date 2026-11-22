package com.capsule.session.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** connections 테이블 (2026-11-23 study_sessions에서 이름 변경). 자바 이름은 다음 정리 때 바꾼다 */
@Entity
@Table(name = "connections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long userId;
    /** 연결한 메이트 (옛 session_characters 1행) */
    private long characterId;

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

    public StudySession(long userId, long characterId, Instant now) {
        this.userId = userId;
        this.characterId = characterId;
        this.startedAt = now;
        this.lastHeartbeatAt = now;
    }

    public boolean inProgress() {
        return endedAt == null;
    }

    /** 연결당 메이트 1명 */
    public long characterId() {
        return characterId;
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
}
