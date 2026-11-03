package com.capsule.session.api;

import com.capsule.session.api.SessionTypes.SessionEventInfo;
import com.capsule.session.api.SessionTypes.SessionInfo;
import java.util.Optional;

public interface SessionQuery {

    Optional<SessionInfo> find(long sessionId);

    /** 그 세션의 이벤트만 찾음 (다른 세션 eventId면 빈 값) */
    Optional<SessionEventInfo> findEvent(long sessionId, long eventId);
}
