package com.capsule.dialogue.repository;

import com.capsule.dialogue.domain.SafetyStrike;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SafetyStrikeRepository extends JpaRepository<SafetyStrike, Long> {

    /** 위반 한 번 기록. 같은 요청(재전송 · 이어받기)은 한 번만 (답장 저장 트랜잭션 안에서) */
    @Modifying
    @Query(value = """
            INSERT INTO safety_strikes (user_id, connection_id, request_id, category, created_at)
            VALUES (:userId, :connectionId, :requestId, :category, :now)
            ON CONFLICT (user_id, request_id) DO NOTHING""", nativeQuery = true)
    void record(long userId, long connectionId, UUID requestId, String category, Instant now);

    /** since 이후 위반 시각 (오래된 것부터) */
    @Query(
            "select s.createdAt from SafetyStrike s where s.userId = :userId and s.createdAt > :since order by s.createdAt")
    List<Instant> timesSince(long userId, Instant since);

    boolean existsByUserIdAndRequestId(long userId, UUID requestId);

    /** ids 중 위반 턴이었던 요청 (프롬프트 입력에서 뺄 것) */
    @Query("select s.requestId from SafetyStrike s where s.userId = :userId and s.requestId in :ids")
    Set<UUID> struckAmong(long userId, Collection<UUID> ids);
}
