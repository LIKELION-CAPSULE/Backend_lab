package com.capsule.user.api;

/**
 * 회원 탈퇴 알림 (api.md 27). 탈퇴 트랜잭션 안에서 동기로 발행한다.
 * 받는 쪽은 {@code @EventListener}로 그 사용자의 지울 데이터를 같은 트랜잭션에서 지운다
 * (하나라도 실패하면 탈퇴 전체가 되돌아가서 반쯤 지운 상태가 남지 않게).
 */
public record UserWithdrawnEvent(long userId) {}
