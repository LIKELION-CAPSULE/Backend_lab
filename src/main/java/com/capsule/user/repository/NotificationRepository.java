package com.capsule.user.repository;

import com.capsule.user.domain.Notification;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 같은 (사용자, 종류, dedup_key)가 이미 있으면 무시 (같은 일을 두 번 적지 않게) */
    @Modifying
    @Query(
            value = "INSERT INTO notifications (user_id, type, title, body, link, dedup_key, created_at)"
                    + " VALUES (:userId, :type, :title, :body, :link, :dedupKey, :now)"
                    + " ON CONFLICT (user_id, type, dedup_key) WHERE dedup_key IS NOT NULL DO NOTHING",
            nativeQuery = true)
    int insert(long userId, String type, String title, String body, String link, String dedupKey, Instant now);

    /** 43: 최신순, beforeId보다 오래된 것, since 이후만 (90일) */
    @Query("select n from Notification n where n.userId = :userId and n.id < :beforeId and n.createdAt >= :since"
            + " order by n.id desc")
    List<Notification> findPage(long userId, long beforeId, Instant since, Pageable page);

    @Query(
            "select count(n) from Notification n where n.userId = :userId and n.readAt is null and n.createdAt >= :since")
    int countUnread(long userId, Instant since);

    /** 44: 내 알림 중 고른 것만 (남의 id는 조건에서 빠짐) */
    @Modifying
    @Query("update Notification n set n.readAt = :at where n.userId = :userId and n.readAt is null and n.id in :ids")
    int markRead(long userId, List<Long> ids, Instant at);

    @Modifying
    @Query("update Notification n set n.readAt = :at where n.userId = :userId and n.readAt is null")
    int markAllRead(long userId, Instant at);

    /** 탈퇴 */
    @Modifying
    @Query("delete from Notification n where n.userId = :userId")
    int deleteByUserId(long userId);
}
