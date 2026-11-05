package com.capsule.dialogue.dto;

/**
 * LLM을 부르는 용도. 용도마다 모델 등급·토큰 한도를 따로 정하고(capsule.llm.routes) llm_usages.purpose에 남긴다.
 * 새 용도를 더하면 llm_usages의 CHECK도 새 마이그레이션으로 넓힌다.
 */
public enum LlmPurpose {
    /** 메시지 답장 (SCENE · SIMPLE 파이프라인, 명세 20 · 28) */
    CHAT,
    /** 상태 반응 대사 (명세 19, reaction-llm-triggers에 든 trigger만) */
    REACTION,
    /** 세션 하나를 흐름 카드 한 장으로 (명세 22) */
    SUMMARY,
    /** 기억할 사실 한 줄 쓰기 (결정 모델이 저장 · 고치기로 고른 턴만) */
    MEMORY,
    /** 완료 대사 (명세 22) */
    CLOSING,
    /** 실험실 평가 심사관 */
    EVAL_JUDGE,
    /** 결정: 정해진 보기 중 하나 고르기 + 확신도 (예: 사용자 말의 신호 → 호감도). 글을 쓰지 않는다. Jev 같은 결정 모델로 보낼 수 있다 */
    DECIDE
}
