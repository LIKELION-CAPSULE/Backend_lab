package com.capsule.session.internal.repository;

import com.capsule.session.internal.domain.SessionEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionEventRepository extends JpaRepository<SessionEvent, Long> {

    List<SessionEvent> findBySessionIdOrderByOccurredAtAscIdAsc(long sessionId);
}
