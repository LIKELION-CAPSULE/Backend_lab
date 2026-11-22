package com.capsule.session.repository;

import com.capsule.session.domain.StudySession;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    Optional<StudySession> findByUserIdAndEndedAtIsNull(long userId);

    /** 홈의 마지막 메이트 (api.md 11): 가장 최근에 시작한 연결 */
    Optional<StudySession> findFirstByUserIdOrderByStartedAtDescIdDesc(long userId);

    /** 쓰는 요청끼리(종료 연타, 자동 마감, heartbeat, 이벤트 재전송) 같은 세션을 한 줄로 세운다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StudySession> findWithLockById(long id);

    /** 이 기간에 끝난 연결의 함께한 시간 합 (홈 오늘 시간, api.md 11) */
    @Query("select coalesce(sum(s.presentSeconds), 0) from StudySession s"
            + " where s.userId = :userId and s.endedAt >= :from and s.endedAt < :to")
    int presentSecondsEndedBetween(long userId, Instant from, Instant to);

    /** 이 기간에 끝난 연결의 연결 시간 합 (홈 오늘 시간 · 청소년 3시간, api.md 11 · 16) */
    @Query("select coalesce(sum(s.connectedSeconds), 0) from StudySession s"
            + " where s.userId = :userId and s.endedAt >= :from and s.endedAt < :to")
    int connectedSecondsEndedBetween(long userId, Instant from, Instant to);

    /** 이 메이트와 연결한 적이 있는지 (첫 만남, api.md 16). 지금 만든 연결은 빼고 센다 */
    boolean existsByUserIdAndCharacterIdAndIdNot(long userId, long characterId, long id);

    @Query("select s.id from StudySession s where s.endedAt is null"
            + " and coalesce(s.lastHeartbeatAt, s.startedAt) < :cutoff")
    List<Long> findStaleIds(Instant cutoff);
}
