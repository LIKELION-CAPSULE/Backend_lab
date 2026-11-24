package com.capsule.credit.api;

import java.time.LocalDate;

/**
 * 그날 출석 크레딧을 지급했다 (api.md 43 알림 ATTENDANCE, ADR 0035). 지급 트랜잭션 안에서 동기로 발행한다.
 * user가 받아 알림을 적는다 — user가 이미 credit을 부르므로 credit이 user를 직접 부르면 순환이라 이벤트로
 *
 * @param day 오전 5시 기준 그날 (같은 날 알림은 한 번만)
 */
public record AttendanceGrantedEvent(long userId, int amount, LocalDate day) {}
