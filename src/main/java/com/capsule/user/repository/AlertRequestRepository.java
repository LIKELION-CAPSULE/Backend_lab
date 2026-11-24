package com.capsule.user.repository;

import com.capsule.user.domain.User;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 출시 알림 신청(api.md 45) · 메이트 공개 알림 신청(46). 둘 다 "한 번만 남기고 다시 와도 그대로"라 INSERT … ON CONFLICT 하나씩.
 * 엔티티 없이 SQL로만 (읽는 화면이 없음, 운영이 DB에서 꺼내 씀)
 */
public interface AlertRequestRepository extends Repository<User, Long> {

    /** @return 새로 남겼으면 1, 이미 있으면 0 */
    @Modifying
    @Query(
            value = "INSERT INTO beta_waitlist (email) VALUES (:email) ON CONFLICT (email) DO NOTHING",
            nativeQuery = true)
    int joinWaitlist(String email);

    @Modifying
    @Query(
            value = "INSERT INTO mate_alerts (user_id, character_id) VALUES (:userId, :mateId)"
                    + " ON CONFLICT (user_id, character_id) DO NOTHING",
            nativeQuery = true)
    int requestMateAlert(long userId, long mateId);

    /** 탈퇴 때 (개인 요청이라 남기지 않음) */
    @Modifying
    @Query(value = "DELETE FROM mate_alerts WHERE user_id = :userId", nativeQuery = true)
    int deleteMateAlerts(long userId);
}
