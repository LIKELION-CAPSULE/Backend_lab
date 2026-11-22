package com.capsule.user.repository;

import com.capsule.user.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // user_social_accounts는 조회·추가뿐이라 엔티티 없이 둔다
    @Query(
            value =
                    "SELECT user_id FROM user_social_accounts WHERE provider = :provider AND provider_user_id = :providerUserId",
            nativeQuery = true)
    Optional<Long> findUserIdBySocialAccount(String provider, String providerUserId);

    @Modifying
    @Query(
            value = "INSERT INTO user_social_accounts (provider, provider_user_id, user_id)"
                    + " VALUES (:provider, :providerUserId, :userId)",
            nativeQuery = true)
    void insertSocialAccount(String provider, String providerUserId, long userId);

    /** 베타 정원 (api.md 4 · 6): 탈퇴하지 않은 가입자 수 */
    long countByWithdrawnAtIsNull();

    boolean existsByReferralCode(String referralCode);

    /** 가입 기기 (api.md 4 · 6). 같은 기기 · 같은 사용자는 한 행 */
    @Modifying
    @Query(
            value = "INSERT INTO signup_devices (device_hash, user_id) VALUES (:deviceHash, :userId)"
                    + " ON CONFLICT DO NOTHING",
            nativeQuery = true)
    void insertSignupDevice(String deviceHash, long userId);

    /** 설정 (api.md 29 · 30). jsonb를 글자로 주고받는다 */
    /** 탈퇴한 사용자는 빈 값 (남은 액세스 토큰으로 부르면 401) */
    @Query(value = "SELECT settings::text FROM users WHERE id = :userId AND withdrawn_at IS NULL", nativeQuery = true)
    Optional<String> findSettings(long userId);

    /** 변경용: 같은 사용자의 PATCH가 동시에 오면 차례로 (한쪽 변경이 사라지지 않게) */
    @Query(
            value = "SELECT settings::text FROM users WHERE id = :userId AND withdrawn_at IS NULL FOR UPDATE",
            nativeQuery = true)
    Optional<String> findSettingsForUpdate(long userId);

    @Modifying
    @Query(
            value = "UPDATE users SET settings = CAST(:settings AS jsonb), updated_at = now() WHERE id = :userId",
            nativeQuery = true)
    void updateSettings(long userId, String settings);

    /** 탈퇴 (api.md 27): 같은 소셜 계정으로 다시 가입할 수 있게 연결을 지운다 */
    @Modifying
    @Query(value = "DELETE FROM user_social_accounts WHERE user_id = :userId", nativeQuery = true)
    void deleteSocialAccounts(long userId);
}
