package com.capsule.session.api;

import com.capsule.session.api.SessionTypes.SessionEventInfo;
import com.capsule.session.api.SessionTypes.SessionInfo;
import java.util.Optional;

public interface SessionQuery {

    Optional<SessionInfo> find(long sessionId);

    /** 그 세션의 이벤트만 찾음 (다른 세션 eventId면 빈 값) */
    Optional<SessionEventInfo> findEvent(long sessionId, long eventId);

    /**
     * 지금 쉬는 시간 · 일시정지 중인지: 마지막 상태 이벤트가 BREAK (동작 이벤트는 건너뜀, api.md 18).
     * 대화(20)가 답 길이 · 말하는 방식을 고를 때 쓴다 (공부 중 1~2문장, 쉬는 시간 2~4문장)
     */
    boolean onBreak(long sessionId);
}
