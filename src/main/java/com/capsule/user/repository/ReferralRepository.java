package com.capsule.user.repository;

import com.capsule.user.domain.Referral;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ReferralRepository extends JpaRepository<Referral, Long> {

    boolean existsByInviteeId(long inviteeId);

    Optional<Referral> findByInviteeId(long inviteeId);

    /** 22 정산용: 끝나는 연결마다 잠그고 본다 (같은 보상을 두 번 주지 않게) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Referral r WHERE r.inviteeId = :inviteeId")
    Optional<Referral> findForUpdateByInviteeId(long inviteeId);

    List<Referral> findByInviterIdOrderByIdDesc(long inviterId);

    long countByInviterIdAndInviterRewardedTrue(long inviterId);

    /** 1이면 넣음, 0이면 이미 입력함 (invitee_id UNIQUE) */
    @Modifying
    @Query(
            value = "INSERT INTO referrals (inviter_id, invitee_id, device_hash, entered_at)"
                    + " VALUES (:inviterId, :inviteeId, :deviceHash, :now) ON CONFLICT (invitee_id) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(long inviterId, long inviteeId, String deviceHash, Instant now);

    /** 같은 기기 (36): 입력한 기기 값이나 내 가입 기기 중 하나가 초대한 사람의 가입 기기와 같음 */
    @Query(
            value =
                    "SELECT EXISTS (SELECT 1 FROM signup_devices d WHERE d.user_id = :inviterId AND (d.device_hash = :deviceHash"
                            + " OR d.device_hash IN (SELECT m.device_hash FROM signup_devices m WHERE m.user_id = :inviteeId)))",
            nativeQuery = true)
    boolean sharesDevice(long inviterId, long inviteeId, String deviceHash);

    /** 초대한 사람 보상 10명 한도를 지키려고 그 사람 기준으로 한 줄로 세운다 (users 행 잠금) */
    @Query(value = "SELECT id FROM users WHERE id = :userId FOR UPDATE", nativeQuery = true)
    Optional<Long> lockUser(long userId);
}
