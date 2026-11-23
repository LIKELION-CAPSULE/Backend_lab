package com.capsule.session.domain;

import jakarta.persistence.Column;
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

    public static final String NORMAL = "NORMAL";
    public static final String AUTO_CLOSED = "AUTO_CLOSED";
    public static final String AUTO_AWAY = "AUTO_AWAY";
    public static final String MINOR_LIMIT = "MINOR_LIMIT";

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
    // 마지막 heartbeat 누적값 (api.md 21). 자동 마감 · 이어하기 · 청소년 남은 시간에 쓴다
    private Integer heartbeatConnectedSeconds;
    private Integer heartbeatPresentSeconds;
    private Integer heartbeatCameraOnSeconds;
    private Integer heartbeatPipSeconds;
    // 끊을 때 저장 (api.md 22)
    private int connectedSeconds;
    private int presentSeconds;
    private int awaySeconds;
    private int cameraOnSeconds;
    private int pipSeconds;
    /**
     * 답장마다 SQL로 +1 (StudySessionRepository.incrementMessageCount). 엔티티는 쓰지 않는다: heartbeat · 끊기가
     * 먼저 읽은 값으로 행 전체를 저장하면 그사이 올린 수가 사라져서
     */
    @Column(insertable = false, updatable = false)
    private int messageCount;

    private int returnCount;
    private String mood;
    private String closingLine;
    /**
     * 지금 배경 (character_backgrounds.code, NULL이면 메이트 기본, ADR 0032). 답장 때 SQL로 바꾼다
     * (StudySessionRepository.updateBackground). 엔티티는 쓰지 않는다: messageCount와 같은 이유
     */
    @Column(insertable = false, updatable = false)
    private String backgroundCode;

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

    /** 생존 신호만. 시간 값이 잘못된 heartbeat · 이어하기용 (저장된 값은 그대로 둠) */
    public void heartbeat(Instant now) {
        this.lastHeartbeatAt = now;
    }

    /** heartbeat(api.md 21)가 보낸 그 시점까지의 누적값 */
    public void heartbeat(Instant now, ConnectionTimes t) {
        heartbeat(now);
        this.heartbeatConnectedSeconds = t.connected();
        this.heartbeatPresentSeconds = t.present();
        this.heartbeatCameraOnSeconds = t.cameraOn();
        this.heartbeatPipSeconds = t.pip();
    }

    /** 마지막 heartbeat 값. 받은 적 없으면 0 */
    public ConnectionTimes lastHeartbeatTimes() {
        if (heartbeatConnectedSeconds == null) {
            return ConnectionTimes.ZERO;
        }
        return new ConnectionTimes(
                heartbeatConnectedSeconds,
                heartbeatPresentSeconds,
                heartbeatCameraOnSeconds == null ? heartbeatPresentSeconds : heartbeatCameraOnSeconds,
                heartbeatPipSeconds == null ? 0 : heartbeatPipSeconds);
    }

    /** 끊기 기록. 완료 대사는 대화 쪽 마무리가 끝난 뒤 {@link #recordClosingLine(String)}로 남긴다 */
    public void close(Instant endedAt, String endType, ConnectionTimes t, int awaySeconds, int returnCount) {
        this.endedAt = endedAt;
        this.endType = endType;
        this.connectedSeconds = t.connected();
        this.presentSeconds = t.present();
        this.cameraOnSeconds = t.cameraOn();
        this.pipSeconds = t.pip();
        this.awaySeconds = awaySeconds;
        this.returnCount = returnCount;
    }

    public void recordClosingLine(String closingLine) {
        this.closingLine = closingLine;
    }
}
