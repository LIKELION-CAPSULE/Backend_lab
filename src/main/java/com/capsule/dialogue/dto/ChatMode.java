package com.capsule.dialogue.dto;

/** 대화 모드 (api.md 20 · 28, 2026-11-23). 답장(chat_messages.mode)에 저장한다 */
public enum ChatMode {
    /** 기본: 크레딧을 보지도 깎지도 않음 */
    BASIC,
    /** 고급: 1턴 30 (답장이 성공한 뒤에만) */
    ADVANCED
}
