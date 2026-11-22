package com.capsule.credit.repository;

import com.capsule.credit.domain.CreditLedgerEntry;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CreditLedgerRepository extends JpaRepository<CreditLedgerEntry, Long> {

    Optional<CreditLedgerEntry> findByUserIdAndRequestId(long userId, UUID requestId);

    /** 34: 최신순, beforeId보다 오래된 것 */
    @Query("select e from CreditLedgerEntry e where e.userId = :userId and e.type in :types and e.id < :beforeId"
            + " order by e.id desc")
    List<CreditLedgerEntry> findPage(long userId, Collection<String> types, long beforeId, Pageable page);

    /** 33 todayUsed: 그날(오전 5시부터) 쓴 양 */
    @Query("select coalesce(-sum(e.change), 0) from CreditLedgerEntry e where e.userId = :userId and e.type = 'USE'"
            + " and e.createdAt >= :from")
    int usedSince(long userId, Instant from);
}
