package com.capsule.relation.repository;

import com.capsule.relation.domain.Relation;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RelationRepository extends JpaRepository<Relation, Long> {

    Optional<Relation> findByUserIdAndCharacterId(long userId, long characterId);

    /** 관계 하나의 반영을 줄 세운다: 중복 확인 후 기록이 겹치지 않게 */
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

    /** level 컬럼은 계산값(자리 지킴 시간)을 같이 저장해 둔다: DB만 보고도 알 수 있게 */
    @Modifying(flushAutomatically = true)
    @Query("update Relation r set r.totalStudySeconds = r.totalStudySeconds + :study,"
            + " r.totalPresentSeconds = r.totalPresentSeconds + :present, r.level = :level, r.lastMetAt = :now"
            + " where r.id = :id")
    void addStudy(long id, int study, int present, int level, Instant now);

    @Modifying(flushAutomatically = true)
    @Query("update Relation r set r.streakDays = :days, r.lastStudyDate = :date where r.id = :id")
    void updateStreak(long id, int days, LocalDate date);

    /** 친밀도 누적 (2026-11-23). 단계는 계산값을 같이 저장 */
    @Modifying(flushAutomatically = true)
    @Query("update Relation r set r.creditedSeconds = r.creditedSeconds + :credited,"
            + " r.totalConnectedSeconds = r.totalConnectedSeconds + :connected, r.stage = :stage where r.id = :id")
    void addIntimacy(long id, int credited, int connected, int stage);

    @Query(
            value = "SELECT credited_seconds FROM relation_daily WHERE relation_id = :relationId AND day = :day",
            nativeQuery = true)
    Optional<Integer> creditedOn(long relationId, LocalDate day);

    @Modifying
    @Query(value = """
            INSERT INTO relation_daily (relation_id, day, credited_seconds) VALUES (:relationId, :day, :credited)
            ON CONFLICT (relation_id, day) DO UPDATE SET credited_seconds = relation_daily.credited_seconds + :credited""", nativeQuery = true)
    void addDaily(long relationId, LocalDate day, int credited);

    @Query(
            value = "SELECT chat_turns FROM relation_daily WHERE relation_id = :relationId AND day = :day",
            nativeQuery = true)
    Optional<Integer> chatTurnsOn(long relationId, LocalDate day);

    @Modifying
    @Query(value = """
            INSERT INTO relation_daily (relation_id, day, chat_turns) VALUES (:relationId, :day, 1)
            ON CONFLICT (relation_id, day) DO UPDATE SET chat_turns = relation_daily.chat_turns + 1""", nativeQuery = true)
    void addChatTurn(long relationId, LocalDate day);

    @Modifying
    @Query(value = """
            INSERT INTO user_unlocked_contents (user_id, content_id) VALUES (:userId, :contentId)
            ON CONFLICT DO NOTHING""", nativeQuery = true)
    void unlock(long userId, long contentId);

    /** 내 메이트(api.md 37)의 해금 여부 */
    @Query(value = "SELECT content_id FROM user_unlocked_contents WHERE user_id = :userId", nativeQuery = true)
    List<Long> unlockedContentIds(long userId);

    /** 깎인 점수가 0이어도(0점에서 심한 말) 그날 오름 멈춤이 걸리게 기록 대신 여기에 남긴다 */
    @Modifying(flushAutomatically = true)
    @Query("update Relation r set r.lastSevereAt = :at where r.id = :id")
    void markSevere(long id, Instant at);
}
