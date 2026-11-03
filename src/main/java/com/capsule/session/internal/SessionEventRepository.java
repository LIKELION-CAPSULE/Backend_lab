package com.capsule.session.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface SessionEventRepository extends JpaRepository<SessionEvent, Long> {

    List<SessionEvent> findBySessionIdOrderByOccurredAtAscIdAsc(long sessionId);
}
