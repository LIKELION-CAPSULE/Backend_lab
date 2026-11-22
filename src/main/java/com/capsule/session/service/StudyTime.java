package com.capsule.session.service;

import com.capsule.session.domain.SessionEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 이벤트로 계산하는 자리 비움 (api.md 22). AWAY부터 다음 PRESENT · RETURN(또는 끝 시각)까지.
 * 함께한 시간 · 연결 시간은 프론트가 보내고, 자리 비움만 서버가 이벤트로 센다
 */
public final class StudyTime {

    private StudyTime() {}

    /** events는 시각 · id 순. 끝 시각 뒤 이벤트는 끝 시각에서 자른다 */
    public static int awaySeconds(List<SessionEvent> events, Instant end) {
        long millis = 0;
        Instant awayFrom = null;
        for (SessionEvent e : events) {
            Instant at = e.getOccurredAt().isBefore(end) ? e.getOccurredAt() : end;
            switch (e.getType()) {
                case SessionEvent.AWAY -> {
                    if (awayFrom == null && !e.isFalsePositive()) awayFrom = at;
                }
                case SessionEvent.PRESENT, SessionEvent.RETURN -> {
                    if (awayFrom != null) {
                        millis += Duration.between(awayFrom, at).toMillis();
                        awayFrom = null;
                    }
                }
                default -> {} // IDLE_TALK · 옛 이벤트는 구간을 끊지 않음
            }
        }
        if (awayFrom != null && end.isAfter(awayFrom)) {
            millis += Duration.between(awayFrom, end).toMillis();
        }
        return (int) (millis / 1000);
    }

    /** 이 연결에서 몇 번째 자리 비움인지 → 반응 강도 1~3 (api.md 18) */
    public static int awayIntensity(List<SessionEvent> events) {
        long count = events.stream()
                .filter(e -> SessionEvent.AWAY.equals(e.getType()))
                .count();
        return (int) Math.max(1, Math.min(3, count));
    }
}
