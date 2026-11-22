package com.capsule.session.api;

/** 연결의 대화 수 (api.md 22 · 23 messageCount, 2026-11-23 6단계). 대화 모듈이 답장을 저장할 때 부른다 */
public interface ConnectionMessageCounter {

    /**
     * 진행 중인 연결이면 대화 수를 1 올리고 true. 이미 끝났거나 없으면 false (답하는 사이 끝남 → 부른 쪽이 롤백)
     */
    boolean countReply(long connectionId);
}
