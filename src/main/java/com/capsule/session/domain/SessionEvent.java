package com.capsule.session.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "session_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionEvent {

    public static final String FOCUS = "FOCUS";
    public static final String AWAY = "AWAY";
    public static final String RETURN = "RETURN";
    public static final String BREAK = "BREAK";
    // 동작 이벤트 (api.md 18): 감지된 순간 1건. 상태 · 시간 계산에 쓰지 않는다
    public static final String DROWSY = "DROWSY";
    public static final String YAWN = "YAWN";
    public static final String WAVE = "WAVE";
    public static final String HEAD_TURN = "HEAD_TURN";
    /** STATE: 공부 · 자리 비움 · 휴식 구간을 나눈다 */
    public static final Set<String> STATE_TYPES = Set.of(FOCUS, AWAY, RETURN, BREAK);
    /** MOTION: 상태를 바꾸지 않는다 */
    public static final Set<String> MOTION_TYPES = Set.of(DROWSY, YAWN, WAVE, HEAD_TURN);

    public static final Set<String> TYPES = Set.of(FOCUS, AWAY, RETURN, BREAK, DROWSY, YAWN, WAVE, HEAD_TURN);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long sessionId;
    private String type;
    private boolean falsePositive;
    private Instant occurredAt;

    public SessionEvent(long sessionId, String type, Instant occurredAt) {
        this.sessionId = sessionId;
        this.type = type;
        this.occurredAt = occurredAt;
    }

    /** 상태 이벤트인지. 동작 이벤트는 휴식 판정 · 이어하기 · 시간 계산에서 건너뛴다 (api.md 18) */
    public boolean isState() {
        return STATE_TYPES.contains(type);
    }

    /** '아니야' 버튼 (오탐이면 집중으로 계산) */
    public void markFalsePositive() {
        this.falsePositive = true;
    }
}
