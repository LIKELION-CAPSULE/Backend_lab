package com.capsule.session.adapter;

import com.capsule.session.api.SessionQuery;
import com.capsule.session.api.SessionTypes.SessionEventInfo;
import com.capsule.session.api.SessionTypes.SessionInfo;
import com.capsule.session.domain.SessionEvent;
import com.capsule.session.repository.SessionEventRepository;
import com.capsule.session.repository.StudySessionRepository;
import java.util.List;
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
                        s.getId(), s.getUserId(), s.getRoomId(), s.characterId(), s.getStartedAt(), s.getEndedAt()));
    }

    @Override
    public Optional<SessionEventInfo> findEvent(long sessionId, long eventId) {
        List<SessionEvent> list = events.findBySessionIdOrderByOccurredAtAscIdAsc(sessionId);
        for (int i = 0; i < list.size(); i++) {
            SessionEvent e = list.get(i);
            if (e.getId() == eventId) {
                boolean firstFocus = SessionEvent.FOCUS.equals(e.getType())
                        && list.subList(0, i).stream().noneMatch(p -> SessionEvent.FOCUS.equals(p.getType()));
                // 바로 앞 '상태' 이벤트가 BREAK인지 (사이에 낀 동작 이벤트는 건너뜀, api.md 18)
                boolean afterBreak = list.subList(0, i).stream()
                        .filter(SessionEvent::isState)
                        .reduce((a, b) -> b)
                        .map(p -> SessionEvent.BREAK.equals(p.getType()))
                        .orElse(false);
                return Optional.of(new SessionEventInfo(
                        e.getId(), sessionId, e.getType(), e.getOccurredAt(), firstFocus, afterBreak));
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean onBreak(long sessionId) {
        return events.findBySessionIdOrderByOccurredAtAscIdAsc(sessionId).stream()
                .filter(SessionEvent::isState)
                .reduce((a, b) -> b)
                .map(e -> SessionEvent.BREAK.equals(e.getType()))
                .orElse(false);
    }
}
