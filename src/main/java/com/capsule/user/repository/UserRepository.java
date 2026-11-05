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

    /** 탈퇴 (api.md 27): 같은 소셜 계정으로 다시 가입할 수 있게 연결을 지운다 */
    @Modifying
    @Query(value = "DELETE FROM user_social_accounts WHERE user_id = :userId", nativeQuery = true)
    void deleteSocialAccounts(long userId);
}
