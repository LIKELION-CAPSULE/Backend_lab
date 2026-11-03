package com.capsule.user.api;

import com.capsule.user.api.UserTypes.ActiveSessionSummary;
import java.util.Optional;

/**
 * 진행 중 세션 조회 자리. user는 인터페이스만 두고 session 모듈이 구현한다.
 * session이 이미 user를 부르므로, user가 session을 직접 부르면 순환이 생겨서 의존을 뒤집었다 (skeleton-plan 0장).
 */
public interface ActiveSessionLookup {

    Optional<ActiveSessionSummary> find(long userId);
}
