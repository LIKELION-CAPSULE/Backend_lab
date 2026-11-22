package com.capsule.session.adapter;

import com.capsule.session.api.SessionQuery;
import com.capsule.session.api.SessionTypes.SessionEventInfo;
import com.capsule.session.api.SessionTypes.SessionInfo;
import com.capsule.session.repository.SessionEventRepository;
import com.capsule.session.repository.StudySessionRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** dialogue가 쓰는 조회. SessionClosingHandler(dialogue 구현)에 의존하지 않게 SessionService와 나눴다 (빈 순환 방지) */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SessionQueryAdapter implements SessionQuery {

    private final StudySessionRepository sessions;
    private final SessionEventRepository events;

    @Override
    public Optional<SessionInfo> find(long sessionId) {
        return sessions.findById(sessionId)
                .map(s -> new SessionInfo(
                        // 룸이 없어져 roomId는 0 (2026-11-23). SessionInfo 모양은 dialogue가 써서 그대로 둔다
                        s.getId(), s.getUserId(), 0, s.characterId(), s.getStartedAt(), s.getEndedAt()));
    }

    @Override
    public Optional<SessionEventInfo> findEvent(long sessionId, long eventId) {
        // 2026-11-23 베타 이벤트 4종: FOCUS · BREAK가 없어져 firstFocusOfSession · afterBreak는 늘 false
        return events.findById(eventId)
                .filter(e -> e.getSessionId() == sessionId)
                .map(e -> new SessionEventInfo(e.getId(), sessionId, e.getType(), e.getOccurredAt(), false, false));
    }

    /** 쉬는 시간이 없어져 늘 false (2026-11-23). dialogue가 답 길이를 고를 때 쓴다 */
    @Override
    public boolean onBreak(long sessionId) {
        return false;
    }
}
