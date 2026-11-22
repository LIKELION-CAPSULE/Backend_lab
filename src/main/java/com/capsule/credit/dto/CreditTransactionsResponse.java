package com.capsule.credit.dto;

import java.time.Instant;
import java.util.List;

/** api.md 34 크레딧 내역 (최신순) */
public record CreditTransactionsResponse(List<Item> items, String nextCursor) {

    /** type = ATTENDANCE · INVITE · USE · EXPIRE, change는 지급 +, 사용 · 소멸 − */
    public record Item(long id, String type, String title, int change, int balanceAfter, Instant createdAt) {}
}
