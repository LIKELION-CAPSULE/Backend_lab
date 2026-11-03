package com.capsule.session.api;

import com.capsule.session.api.SessionTypes.SessionClosing;

/**
 * 공부 종료 때 대화 쪽 마무리 자리. session은 인터페이스만 두고 dialogue 모듈이 구현한다.
 * dialogue가 이미 session을 부르므로, session이 dialogue를 직접 부르면 순환이 생겨서 의존을 뒤집었다 (skeleton-plan 0장).
 * 공부 종료 트랜잭션 안에서 불린다 (api.md 22 "한 트랜잭션").
 */
public interface SessionClosingHandler {

    /** 이번 세션 대화를 요약에 합치고 메시지를 지운 뒤, 완료 대사(closingLine)를 돌려준다. session이 study_sessions.closing_line에 저장 */
    String close(SessionClosing closing);
}
