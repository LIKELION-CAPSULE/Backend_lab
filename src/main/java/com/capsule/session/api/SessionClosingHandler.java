package com.capsule.session.api;

import com.capsule.session.api.SessionTypes.SessionClosing;

/**
 * 공부 종료 때 대화 쪽 마무리 자리. session은 인터페이스만 두고 dialogue 모듈이 구현한다.
 * dialogue가 이미 session을 부르므로, session이 dialogue를 직접 부르면 순환이 생겨서 의존을 뒤집었다 (skeleton-plan 0장).
 * 두 단계로 부른다: {@link #draft}는 트랜잭션 밖에서 LLM을 기다리고(DB 연결을 잡지 않음), {@link #close(SessionClosing, Draft)}는
 * 공부 종료 트랜잭션 안에서 저장만 한다 (api.md 22 "한 트랜잭션"은 저장 쪽).
 */
public interface SessionClosingHandler {

    /** dialogue가 만든 마무리 초안. session은 내용을 모르고 그대로 돌려준다 */
    interface Draft {}

    /** 트랜잭션 밖에서 먼저: 이번 세션 카드와 완료 대사를 만든다(LLM, 최대 5초). 대화 데이터는 쓰지 않는다 (LLM 호출 기록만 남음) */
    default Draft draft(SessionClosing closing) {
        return null;
    }

    /**
     * 공부 종료 트랜잭션 안: 초안을 저장하고 메시지를 지운 뒤, 완료 대사(closingLine)를 돌려준다. session이 study_sessions.closing_line에 저장.
     * draft가 null이면(자동 마감 배치) 여기서 만든다: 그동안 LLM을 트랜잭션 안에서 기다린다
     */
    String close(SessionClosing closing, Draft draft);
}
