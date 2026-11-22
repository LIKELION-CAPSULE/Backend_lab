package com.capsule.user.api;

/**
 * 연결 주인 확인 자리 (api.md 42 베타 피드백의 connectionId). user는 인터페이스만 두고 session 모듈이 구현한다.
 * session이 이미 user를 부르므로 의존을 뒤집었다 (ActiveSessionLookup과 같은 이유)
 */
public interface ConnectionOwnerLookup {

    /** 이 사용자의 연결이면 true (없거나 남의 연결이면 false) */
    boolean owns(long userId, long connectionId);
}
