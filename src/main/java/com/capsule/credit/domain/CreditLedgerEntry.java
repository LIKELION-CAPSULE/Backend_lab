package com.capsule.credit.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 크레딧 내역 한 줄 (credit_ledger, api.md 34). balanceAfter = 이 변동 뒤 전체 잔액 */
@Entity
@Table(name = "credit_ledger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditLedgerEntry {

    public static final String ATTENDANCE = "ATTENDANCE";
    public static final String INVITE = "INVITE";
    public static final String USE = "USE";
    public static final String EXPIRE = "EXPIRE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long userId;
    private String type;
    private String title;
    private int change;
    private int balanceAfter;
    private UUID requestId;
    private Instant createdAt;

    public CreditLedgerEntry(
            long userId, String type, String title, int change, int balanceAfter, UUID requestId, Instant now) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.change = change;
        this.balanceAfter = balanceAfter;
        this.requestId = requestId;
        this.createdAt = now;
    }
}
