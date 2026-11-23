package com.capsule.session.api;

/**
 * 연결의 지금 배경 (api.md 16 · 17 · 20, 2026-11-23, ADR 0032). 대화 모듈이 답장 때 읽고 바꾼다.
 * 값은 content의 배경 code. null이면 메이트 기본 배경
 */
public interface ConnectionBackgrounds {

    /** 이 연결의 지금 배경 code. 바꾼 적 없거나 연결이 없으면 null */
    String current(long connectionId);

    /** 진행 중인 연결이면 배경을 바꾸고 true. 이미 끝났거나 없으면 false. 부른 쪽 트랜잭션에 같이 들어간다 */
    boolean change(long connectionId, String code);
}
