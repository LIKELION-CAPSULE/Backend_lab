package com.capsule.session.adapter;

import com.capsule.session.api.SessionQuery;
import com.capsule.session.api.SessionTypes.SessionEventInfo;
import com.capsule.session.api.SessionTypes.SessionInfo;
import com.capsule.session.domain.SessionEvent;
import com.capsule.session.repository.SessionEventRepository;
import com.capsule.session.repository.StudySessionRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** dialogue가 쓰는 조회. SessionClosingHandler(dialogue 구현)에 의존하지 않게 SessionService와 나눴다 (빈 순환 방지) */
@Service
@Transactional(readOnly = true)
class SessionQueryAdapter implements SessionQuery {

    private final StudySessionRepository sessions;
    private final SessionEventRepository events;

    SessionQueryAdapter(StudySessionRepository sessions, SessionEventRepository events) {
        this.sessions = sessions;
        this.events = events;
    }

    @Override
    public Optional<SessionInfo> find(long sessionId) {
        return sessions.findById(sessionId)
                .map(s -> new SessionInfo(s.id(), s.userId(), s.roomId(), s.characterId(), s.startedAt(), s.endedAt()));
    }

    @Override
    public Optional<SessionEventInfo> findEvent(long sessionId, long eventId) {
        List<SessionEvent> list = events.findBySessionIdOrderByOccurredAtAscIdAsc(sessionId);
        for (int i = 0; i < list.size(); i++) {
            SessionEvent e = list.get(i);
            if (e.id() == eventId) {
                boolean firstFocus = SessionEvent.FOCUS.equals(e.type())
                        && list.subList(0, i).stream().noneMatch(p -> SessionEvent.FOCUS.equals(p.type()));
                boolean afterBreak =
                        i > 0 && SessionEvent.BREAK.equals(list.get(i - 1).type());
                return Optional.of(
                        new SessionEventInfo(e.id(), sessionId, e.type(), e.occurredAt(), firstFocus, afterBreak));
            }
        }
        return Optional.empty();
    }
}
