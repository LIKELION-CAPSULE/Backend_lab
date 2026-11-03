package com.capsule.relation.internal.repository;

import com.capsule.relation.internal.domain.Relation;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RelationRepository extends JpaRepository<Relation, Long> {

    Optional<Relation> findByUserIdAndCharacterId(long userId, long characterId);

    /** 관계 하나의 반영을 줄 세운다: 중복 확인 후 기록(FIRST_MEET은 UNIQUE가 없음)이 겹치지 않게 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Relation findLockedByUserIdAndCharacterId(long userId, long characterId);

    @Modifying
    @Query(value = """
            INSERT INTO user_character_relations (user_id, character_id, first_met_at, last_met_at)
            VALUES (:userId, :characterId, :now, :now)
            ON CONFLICT (user_id, character_id) DO NOTHING""", nativeQuery = true)
    void insertIfAbsent(long userId, long characterId, Instant now);

    @Modifying
    @Query(value = """
            INSERT INTO user_character_relations (user_id, character_id, first_met_at, last_met_at)
            VALUES (:userId, :characterId, :now, :now)
            ON CONFLICT (user_id, character_id) DO UPDATE SET last_met_at = EXCLUDED.last_met_at""", nativeQuery = true)
    void meet(long userId, long characterId, Instant now);

    // clearAutomatically는 쓰지 않는다: 같은 트랜잭션에 있는 다른 모듈(세션 종료 등)의 엔티티까지 떼어내 변경이 사라진다.
    // 갱신 뒤에는 RelationService가 그 관계 행만 refresh한다
    @Modifying(flushAutomatically = true)
    @Query("update Relation r set r.relationPoints = r.relationPoints + :delta where r.id = :id")
    void addPoints(long id, int delta);

    @Modifying(flushAutomatically = true)
    @Query("update Relation r set r.totalStudySeconds = r.totalStudySeconds + :seconds, r.lastMetAt = :now"
            + " where r.id = :id")
    void addStudySeconds(long id, int seconds, Instant now);
}
