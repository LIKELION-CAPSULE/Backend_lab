package com.capsule.session.internal.repository;

import com.capsule.session.internal.domain.StudySession;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    Optional<StudySession> findByUserIdAndEndedAtIsNull(long userId);

    /** 쓰는 요청끼리(종료 연타, 자동 마감, heartbeat, 이벤트 재전송) 같은 세션을 한 줄로 세운다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StudySession> findWithLockById(long id);

    @Query("select s.id from StudySession s where s.endedAt is null"
            + " and coalesce(s.lastHeartbeatAt, s.startedAt) < :cutoff")
    List<Long> findStaleIds(Instant cutoff);
}
