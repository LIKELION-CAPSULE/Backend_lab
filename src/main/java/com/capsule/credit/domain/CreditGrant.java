package com.capsule.credit.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 크레딧 지급 건 (credit_grants). 잔액 = 소멸 전 지급 건의 remaining 합 (ADR 0031) */
@Entity
@Table(name = "credit_grants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditGrant {

    public static final String ATTENDANCE = "ATTENDANCE";
    public static final String INVITE = "INVITE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long userId;
    private String kind;
    private int amount;
    private int remaining;
    private LocalDate grantedOn;
    private String source;
    private Instant expiresAt;
    private Instant expiredAt;
    private Instant createdAt;

    private CreditGrant(
            long userId, String kind, int amount, LocalDate grantedOn, String source, Instant expiresAt, Instant now) {
        this.userId = userId;
        this.kind = kind;
        this.amount = amount;
        this.remaining = amount;
        this.grantedOn = grantedOn;
        this.source = source;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    public static CreditGrant attendance(long userId, int amount, LocalDate day, Instant expiresAt, Instant now) {
        return new CreditGrant(userId, ATTENDANCE, amount, day, null, expiresAt, now);
    }

    public static final String INVITER_SUFFIX = "-inviter";

    /** 초대분 중복 방지 키: referral-<초대 id>-inviter | -invitee (33 invitedFriends는 -inviter만 센다) */
    public static String inviteSource(long referralId, boolean inviter) {
        return "referral-" + referralId + (inviter ? INVITER_SUFFIX : "-invitee");
    }

    public static CreditGrant invite(long userId, int amount, String source, Instant now) {
        return new CreditGrant(userId, INVITE, amount, null, source, null, now);
    }

    public boolean usable(Instant now) {
        return expiredAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }

    /** 최대 want만큼 쓰고 실제로 쓴 양 */
    public int use(int want) {
        int used = Math.min(want, remaining);
        remaining -= used;
        return used;
    }

    /** 소멸 처리. 남은 양은 그대로 두고 표시만 (내역에 소멸한 양이 남음) */
    public void expire(Instant at) {
        this.expiredAt = at;
    }
}
