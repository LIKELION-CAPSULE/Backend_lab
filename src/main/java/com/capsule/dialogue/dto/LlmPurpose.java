package com.capsule.dialogue.dto;

/**
 * LLM을 부르는 용도. 용도마다 모델 등급·토큰 한도를 따로 정하고(capsule.llm.routes) llm_usages.purpose에 남긴다.
 * 새 용도를 더하면 llm_usages의 CHECK도 새 마이그레이션으로 넓힌다.
 */
public enum LlmPurpose {
    /** 메시지 답장 한 번에 (SIMPLE 파이프라인, 명세 20) */
    CHAT,
    /** 대화 엔진 감독: 이번 턴 연출 계획(JSON) */
    DIRECTOR,
    /** 대화 엔진 배우: 캐릭터 대사 */
    ACTOR,
    /** 대화 엔진 심사: 품질 모드에서 후보 3개 채점 */
    JUDGE,
    /** 상태 반응 대사 (명세 19, reaction-llm-triggers에 든 trigger만) */
    REACTION,
    /** 세션 대화를 지난 요약에 합침 (명세 22) */
    SUMMARY,
    /** 완료 대사 (명세 22) */
    CLOSING,
    /** 실험실 평가 심사관 */
    EVAL_JUDGE
}
