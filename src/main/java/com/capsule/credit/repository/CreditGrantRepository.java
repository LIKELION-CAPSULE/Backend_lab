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

    long countByUserIdAndKind(long userId, String kind);

    /** 사용자 하나의 크레딧 처리를 한 줄로 세운다 (users 행은 다른 모듈 테이블이라 advisory lock, 트랜잭션 끝에 풀림) */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(31, CAST(:userId AS int))) l", nativeQuery = true)
    int lockUser(long userId);
}
