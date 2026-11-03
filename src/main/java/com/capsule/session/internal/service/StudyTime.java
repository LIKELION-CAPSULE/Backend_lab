package com.capsule.session.internal.service;

import com.capsule.session.internal.domain.SessionEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 이벤트로 계산한 시간 (이어하기·자동 마감·종료 공용). 각 상태는 다음 이벤트(또는 끝 시각)까지 이어진다 */
record StudyTime(int presentSeconds, int awaySeconds, int breakSeconds) {

    int studySeconds() {
        return presentSeconds + awaySeconds;
    }

    /** events는 시각·id 순. 끝 시각 뒤의 이벤트는 끝 시각에서 자른다 */
    static StudyTime of(Instant startedAt, List<SessionEvent> events, Instant end) {
        long[] millis = new long[3]; // 자리 지킴 · 자리 비움 · 휴식
        int state = 0; // 첫 이벤트 전 구간은 자리 지킴
        Instant from = startedAt;
        for (SessionEvent e : events) {
            Instant to = e.occurredAt().isBefore(end) ? e.occurredAt() : end;
            if (to.isAfter(from)) {
                millis[state] += Duration.between(from, to).toMillis();
                from = to;
            }
            state = switch (e.type()) {
                case SessionEvent.AWAY -> e.falsePositive() ? 0 : 1; // '아니야' 버튼이면 자리 지킴
                case SessionEvent.BREAK -> 2;
                default -> 0; // FOCUS · RETURN
            };
        }
        if (end.isAfter(from)) {
            millis[state] += Duration.between(from, end).toMillis();
        }
        return new StudyTime((int) (millis[0] / 1000), (int) (millis[1] / 1000), (int) (millis[2] / 1000));
    }
}
