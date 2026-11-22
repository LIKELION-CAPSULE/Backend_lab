package com.capsule.session.domain;

import jakarta.persistence.Column;
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

/** connection_events (2026-11-23 session_events에서 이름 변경). 베타 이벤트 4종 (api.md 18) */
@Entity
@Table(name = "connection_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionEvent {

    /** 얼굴 감지 시작 (처음 · 카메라를 다시 켬) */
    public static final String PRESENT = "PRESENT";
    /** 자리 비움 (10초 이상 안 보임) */
    public static final String AWAY = "AWAY";
    /** 자리 비움 뒤 다시 보임 */
    public static final String RETURN = "RETURN";
    /** 말 걸기 빈도 타이머 */
    public static final String IDLE_TALK = "IDLE_TALK";

    /** API가 받는 type. 옛 FOCUS · BREAK · 동작 이벤트 행은 DB에 기록으로만 남는다 */
    public static final Set<String> TYPES = Set.of(PRESENT, AWAY, RETURN, IDLE_TALK);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "connection_id")
    private long sessionId;

    private String type;
    private boolean falsePositive;
    private Instant occurredAt;

    public SessionEvent(long sessionId, String type, Instant occurredAt) {
        this.sessionId = sessionId;
        this.type = type;
        this.occurredAt = occurredAt;
    }
}
