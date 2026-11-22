package com.capsule.session.service;

import static java.time.temporal.ChronoUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;

import com.capsule.session.domain.SessionEvent;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 자리 비움 계산 (api.md 22): AWAY부터 다음 PRESENT · RETURN(또는 끝)까지 */
class StudyTimeTest {

    static final Instant T0 = Instant.parse("2026-11-07T05:00:00Z");

    @Test
    void awayLastsUntilPresentOrReturn() {
        List<SessionEvent> events = List.of(
                event("PRESENT", 0),
                event("AWAY", 10),
                event("IDLE_TALK", 12), // 자리 비움을 끊지 않음
                event("RETURN", 15),
                event("AWAY", 20),
                event("AWAY", 22), // 이미 비운 중이면 처음 AWAY부터
                event("PRESENT", 30),
                event("AWAY", 45)); // 끝(50분)까지

        assertThat(StudyTime.awaySeconds(events, T0.plus(50, MINUTES))).isEqualTo((5 + 10 + 5) * 60);
    }

    @Test
    void eventsAfterEndAreCut() {
        List<SessionEvent> events = List.of(event("AWAY", 10), event("RETURN", 60));
        assertThat(StudyTime.awaySeconds(events, T0.plus(40, MINUTES))).isEqualTo(30 * 60);
    }

    @Test
    void awayIntensityIsCountUpToThree() {
        assertThat(StudyTime.awayIntensity(List.of(event("AWAY", 0)))).isEqualTo(1);
        assertThat(StudyTime.awayIntensity(List.of(event("AWAY", 0), event("RETURN", 1), event("AWAY", 2))))
                .isEqualTo(2);
        assertThat(StudyTime.awayIntensity(
                        List.of(event("AWAY", 0), event("AWAY", 1), event("AWAY", 2), event("AWAY", 3))))
                .isEqualTo(3);
    }

    static SessionEvent event(String type, int minute) {
        return new SessionEvent(1, type, T0.plus(minute, MINUTES));
    }
}
