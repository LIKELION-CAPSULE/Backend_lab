package com.capsule.credit.repository;

import com.capsule.credit.domain.CreditGrant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CreditGrantRepository extends JpaRepository<CreditGrant, Long> {

    /** 소멸 처리 전 지급 건. 출석분(사라지는 것) 먼저, 그다음 오래된 순 = 쓰는 순서 */
    @Query("select g from CreditGrant g where g.userId = :userId and g.expiredAt is null"
            + " order by case when g.kind = 'ATTENDANCE' then 0 else 1 end, g.id")
    List<CreditGrant> findOpen(long userId);

    boolean existsByUserIdAndKindAndGrantedOn(long userId, String kind, LocalDate grantedOn);

    boolean existsByUserIdAndKindAndSource(long userId, String kind, String source);

    /** 33 invitedFriends: 초대한 사람 몫만 (받은 사람 몫은 친구를 초대한 게 아니라서 빼고 센다) */
    @Query("select count(g) from CreditGrant g where g.userId = :userId and g.kind = 'INVITE'" + " and g.source like '%"
            + CreditGrant.INVITER_SUFFIX + "'")
    long countInviterRewards(long userId);

    /**
     * 사용자 하나의 크레딧 처리를 한 줄로 세운다 (users 행은 다른 모듈 테이블이라 advisory lock, 트랜잭션 끝에 풀림).
     * user id(bigint)를 그대로 키로 쓴다. 이 레포에서 advisory lock은 여기뿐이라 겹칠 키가 없다
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(CAST(:userId AS bigint))) l", nativeQuery = true)
    int lockUser(long userId);
}
