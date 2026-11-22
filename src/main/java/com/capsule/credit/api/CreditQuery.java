package com.capsule.credit.api;

import com.capsule.credit.api.CreditTypes.Credits;

/** 크레딧 요약 (api.md 8 · 16 · 33). */
public interface CreditQuery {

    /**
     * 지금 크레딧. 그날 처음이면 출석분을 지급하고 지난 출석분을 소멸한다 (배치 없음, ADR 0031) → 쓰기 트랜잭션 안에서 부를 것
     */
    Credits current(long userId);
}
