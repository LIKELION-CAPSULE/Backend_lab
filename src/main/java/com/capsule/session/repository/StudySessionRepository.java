package com.capsule.session.repository;

import com.capsule.session.domain.StudySession;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    /** 진행 중인 연결만 (끝난 연결이면 0행) */
    @Modifying
    @Query(
            value = "UPDATE connections SET message_count = message_count + 1 WHERE id = :id AND ended_at IS NULL",
            nativeQuery = true)
    int incrementMessageCount(long id);

    /** 지금 배경 바꾸기 (ADR 0032). 진행 중인 연결만 (끝난 연결이면 0행) */
    @Modifying
    @Query(
            value = "UPDATE connections SET background_code = :code WHERE id = :id AND ended_at IS NULL",
            nativeQuery = true)
    int updateBackground(long id, String code);

    /**
     * 연결 중 단계 상승 알림 (api.md 21): 더 높은 단계일 때만 올린다. 1행이면 이번에 처음 알릴 단계
     * (heartbeat가 겹쳐 와도 한 번만 알리게 조건부 UPDATE)
     */
    @Modifying
    @Query(
            value = "UPDATE connections SET notified_stage = :stage WHERE id = :id AND ended_at IS NULL"
                    + " AND coalesce(notified_stage, :baseline) < :stage",
            nativeQuery = true)
    int raiseNotifiedStage(long id, int baseline, int stage);

    @Query(value = "SELECT background_code FROM connections WHERE id = :id", nativeQuery = true)
    Optional<String> findBackgroundCode(long id);

    Optional<StudySession> findByUserIdAndEndedAtIsNull(long userId);

    /** 홈의 마지막 메이트 (api.md 11): 가장 최근에 시작한 연결. 취소한 연결(22 CANCELLED)은 빼고 */
    @Query("select s from StudySession s where s.userId = :userId"
            + " and (s.endType is null or s.endType <> 'CANCELLED') order by s.startedAt desc, s.id desc")
    List<StudySession> latestConnections(long userId, org.springframework.data.domain.Limit limit);

    default Optional<StudySession> lastConnection(long userId) {
        return latestConnections(userId, org.springframework.data.domain.Limit.of(1)).stream()
                .findFirst();
    }

    /** 쓰는 요청끼리(종료 연타, 자동 마감, heartbeat, 이벤트 재전송) 같은 세션을 한 줄로 세운다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StudySession> findWithLockById(long id);

    /** 홈 오늘 시간 (api.md 11): 이 기간에 끝난 연결의 함께한 시간 · 연결 시간 합. 취소한 연결(22 CANCELLED)은 빼고 */
    @Query("select coalesce(sum(s.presentSeconds), 0) from StudySession s where s.userId = :userId"
            + " and s.endedAt >= :from and s.endedAt < :to and s.endType <> 'CANCELLED'")
    int homePresentSeconds(long userId, Instant from, Instant to);

    @Query("select coalesce(sum(s.connectedSeconds), 0) from StudySession s where s.userId = :userId"
            + " and s.endedAt >= :from and s.endedAt < :to and s.endType <> 'CANCELLED'")
    int homeConnectedSeconds(long userId, Instant from, Instant to);

    /**
     * 이 기간에 끝난 연결의 연결 시간 합 (청소년 3시간, api.md 16). 취소한 연결도 센다: 취소로 끊어 제한을 피하지 못하게
     */
    @Query("select coalesce(sum(s.connectedSeconds), 0) from StudySession s"
            + " where s.userId = :userId and s.endedAt >= :from and s.endedAt < :to")
    int connectedSecondsEndedBetween(long userId, Instant from, Instant to);

    /** 이 메이트와 연결한 적이 있는지 (첫 만남, api.md 16). 지금 만든 연결과 취소한 연결(22 CANCELLED)은 빼고 센다 */
    @Query("select count(s) > 0 from StudySession s where s.userId = :userId and s.characterId = :characterId"
            + " and s.id <> :id and (s.endType is null or s.endType <> 'CANCELLED')")
    boolean metBefore(long userId, long characterId, long id);

    /** 내 메이트 (api.md 37): 끝난 연결을 메이트별로 모은 값, 마지막 연결 시작이 최근인 순. 취소한 연결은 빼고 */
    @Query("select s.characterId as mateId, min(s.startedAt) as firstMetAt, max(s.startedAt) as lastConnectedAt,"
            + " count(s) as connectionCount, coalesce(sum(s.presentSeconds), 0) as presentSeconds,"
            + " coalesce(sum(s.connectedSeconds), 0) as connectedSeconds"
            + " from StudySession s where s.userId = :userId and s.endedAt is not null and s.endType <> 'CANCELLED'"
            + " group by s.characterId order by max(s.startedAt) desc, s.characterId")
    List<MateTotals> mateTotals(long userId);

    /** 내 메이트 recent (api.md 37): 이 메이트와 최근 끝난 연결 5개. 취소한 연결은 빼고 */
    List<StudySession> findTop5ByUserIdAndCharacterIdAndEndedAtIsNotNullAndEndTypeNotOrderByEndedAtDescIdDesc(
            long userId, long characterId, String cancelled);

    default List<StudySession> recentEnded(long userId, long characterId) {
        return findTop5ByUserIdAndCharacterIdAndEndedAtIsNotNullAndEndTypeNotOrderByEndedAtDescIdDesc(
                userId, characterId, StudySession.CANCELLED);
    }

    /** 내 기록 (api.md 38): 이 기간에 시작한 끝난 연결. 취소한 연결은 빼고 */
    @Query("select s from StudySession s where s.userId = :userId and s.endedAt is not null"
            + " and s.endType <> 'CANCELLED'"
            + " and s.startedAt >= :from and s.startedAt < :to order by s.startedAt, s.id")
    List<StudySession> endedStartedBetween(long userId, Instant from, Instant to);

    @Query("select s.id from StudySession s where s.endedAt is null"
            + " and coalesce(s.lastHeartbeatAt, s.startedAt) < :cutoff")
    List<Long> findStaleIds(Instant cutoff);
}
