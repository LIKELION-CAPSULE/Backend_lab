package com.capsule.session.service;

import static java.time.temporal.ChronoUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;

import com.capsule.session.domain.SessionEvent;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class StudyTimeTest {

    static final Instant T0 = Instant.parse("2026-11-07T05:00:00Z");

    @Test
    void eachStateLastsUntilNextEvent() {
        SessionEvent falseAway = event("AWAY", 20);
        falseAway.markFalsePositive();
        List<SessionEvent> events = List.of(
                event("FOCUS", 5),
                event("AWAY", 10),
                event("RETURN", 15),
                falseAway,
                event("BREAK", 30),
                event("FOCUS", 40),
                event("AWAY", 60)); // 끝 시각 뒤 → 무시

        StudyTime time = StudyTime.of(T0, events, T0.plus(50, MINUTES));

        // 자리 지킴: 0~5(첫 이벤트 전) 5~10 15~20 20~30(오탐) 40~50 / 자리 비움: 10~15 / 휴식: 30~40
        assertThat(time).isEqualTo(new StudyTime(35 * 60, 5 * 60, 10 * 60));
        assertThat(time.studySeconds()).isEqualTo(40 * 60);
    }

    @Test
    void motionEventsDoNotChangeState() {
        List<SessionEvent> events = List.of(
                event("FOCUS", 0),
                event("AWAY", 10),
                event("HEAD_TURN", 12), // 자리 비움 중에 와도 자리 지킴으로 바뀌지 않음
                event("RETURN", 15),
                event("BREAK", 20),
                event("YAWN", 25), // 휴식을 끊지 않음
                event("FOCUS", 30));

        StudyTime time = StudyTime.of(T0, events, T0.plus(40, MINUTES));

        // 자리 지킴: 0~10 15~20 30~40 / 자리 비움: 10~15 / 휴식: 20~30
        assertThat(time).isEqualTo(new StudyTime(25 * 60, 5 * 60, 10 * 60));
    }

    static SessionEvent event(String type, int minute) {
        return new SessionEvent(1, type, T0.plus(minute, MINUTES));
    }
}
