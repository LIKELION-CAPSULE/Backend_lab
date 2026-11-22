package com.capsule.credit.service;

import com.capsule.common.time.ClockConfig;
import com.capsule.credit.api.CreditCommands;
import com.capsule.credit.api.CreditQuery;
import com.capsule.credit.api.CreditTypes.Credits;
import com.capsule.credit.api.CreditTypes.SpendResult;
import com.capsule.credit.domain.CreditGrant;
import com.capsule.credit.domain.CreditLedgerEntry;
import com.capsule.credit.repository.CreditGrantRepository;
import com.capsule.credit.repository.CreditLedgerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 크레딧 (api.md 33 '크레딧 규칙', ADR 0031). 사용자마다 advisory lock으로 한 줄로 세운 뒤
 * ① 지난 출석분 소멸 ② 그날 출석분 지급 ③ 조회 · 차감 · 지급 순서로 처리한다 (배치 없음)
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CreditService implements CreditQuery, CreditCommands {

    /** 하루 출석 지급량 (PRD 확정값) */
    public static final int ATTENDANCE_AMOUNT = 1500;
    /** 이 밑이면 low (SCR-34) */
    public static final int LOW_BELOW = 100;
    /** 하루는 오전 5시(KST)에 바뀐다 */
    private static final LocalTime DAY_STARTS_AT = LocalTime.of(5, 0);

    private final CreditGrantRepository grants;
    private final CreditLedgerRepository ledger;
    private final Clock clock;

    @Override
    public Credits current(long userId) {
        Instant now = Instant.now(clock);
        return summary(refresh(userId, now), now);
    }

    @Override
    public SpendResult spend(long userId, int amount, String title, UUID requestId) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        Instant now = Instant.now(clock);
        List<CreditGrant> open = refresh(userId, now);
        if (ledger.findByUserIdAndRequestId(userId, requestId).isPresent()) {
            return new SpendResult(true, summary(open, now)); // 같은 요청은 한 번만
        }
        if (balance(open, now) < amount) return new SpendResult(false, summary(open, now));
        int left = amount;
        for (CreditGrant g : open) { // 출석분(사라지는 것) 먼저
            if (left == 0) break;
            if (g.usable(now)) left -= g.use(left);
        }
        ledger.save(new CreditLedgerEntry(
                userId, CreditLedgerEntry.USE, title, -amount, balance(open, now), requestId, now));
        return new SpendResult(true, summary(open, now));
    }

    @Override
    public boolean grantInvite(long userId, int amount, String source, String title) {
        Instant now = Instant.now(clock);
        List<CreditGrant> open = refresh(userId, now);
        if (grants.existsByUserIdAndKindAndSource(userId, CreditGrant.INVITE, source)) return false;
        CreditGrant g = grants.save(CreditGrant.invite(userId, amount, source, now));
        open.add(g);
        ledger.save(
                new CreditLedgerEntry(userId, CreditLedgerEntry.INVITE, title, amount, balance(open, now), null, now));
        return true;
    }

    /** 33: 요약 + 오늘 쓴 양 · 초대 보상 횟수 */
    public Summary summaryWithUsage(long userId) {
        Instant now = Instant.now(clock);
        Credits credits = summary(refresh(userId, now), now);
        int used = ledger.usedSince(userId, dayStart(now));
        long invited = grants.countByUserIdAndKind(userId, CreditGrant.INVITE);
        return new Summary(credits, used, (int) invited);
    }

    public record Summary(Credits credits, int todayUsed, int invitedFriends) {}

    // ---- 도우미

    /** 잠그고, 지난 출석분 소멸 → 그날 출석분 지급. 소멸 전 지급 건(쓰는 순서)을 돌려준다 */
    private List<CreditGrant> refresh(long userId, Instant now) {
        grants.lockUser(userId);
        List<CreditGrant> open = new java.util.ArrayList<>(grants.findOpen(userId));
        // ① 소멸: 남은 양을 내역에 −로 남기고 표시 (0이면 표시만)
        for (CreditGrant g : List.copyOf(open)) {
            if (g.usable(now)) continue;
            open.remove(g);
            g.expire(now);
            if (g.getRemaining() > 0) {
                ledger.save(new CreditLedgerEntry(
                        userId, CreditLedgerEntry.EXPIRE, "출석분 소멸", -g.getRemaining(), balance(open, now), null, now));
            }
        }
        // ② 그날 출석: 한 번만 (유니크 인덱스가 한 번 더 막음)
        LocalDate day = dayOf(now);
        if (!grants.existsByUserIdAndKindAndGrantedOn(userId, CreditGrant.ATTENDANCE, day)) {
            CreditGrant g = grants.save(CreditGrant.attendance(userId, ATTENDANCE_AMOUNT, day, nextDayStart(now), now));
            open.addFirst(g);
            ledger.save(new CreditLedgerEntry(
                    userId, CreditLedgerEntry.ATTENDANCE, "출석", ATTENDANCE_AMOUNT, balance(open, now), null, now));
        }
        return open;
    }

    private static int balance(List<CreditGrant> open, Instant now) {
        return open.stream()
                .filter(g -> g.usable(now))
                .mapToInt(CreditGrant::getRemaining)
                .sum();
    }

    private Credits summary(List<CreditGrant> open, Instant now) {
        int attendance = open.stream()
                .filter(g -> g.usable(now) && CreditGrant.ATTENDANCE.equals(g.getKind()))
                .mapToInt(CreditGrant::getRemaining)
                .sum();
        int invite = open.stream()
                .filter(g -> g.usable(now) && CreditGrant.INVITE.equals(g.getKind()))
                .mapToInt(CreditGrant::getRemaining)
                .sum();
        int total = attendance + invite;
        return new Credits(total, attendance, nextDayStart(now), invite, total < LOW_BELOW, total == 0);
    }

    static LocalDate dayOf(Instant at) {
        ZonedDateTime local = at.atZone(ClockConfig.KST);
        return local.toLocalTime().isBefore(DAY_STARTS_AT) ? local.toLocalDate().minusDays(1) : local.toLocalDate();
    }

    private static Instant dayStart(Instant now) {
        return dayOf(now).atTime(DAY_STARTS_AT).atZone(ClockConfig.KST).toInstant();
    }

    private static Instant nextDayStart(Instant now) {
        return dayOf(now)
                .plusDays(1)
                .atTime(DAY_STARTS_AT)
                .atZone(ClockConfig.KST)
                .toInstant();
    }
}
