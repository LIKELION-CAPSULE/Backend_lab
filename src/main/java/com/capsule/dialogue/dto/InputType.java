package com.capsule.dialogue.dto;

/** 메시지 입력 방식 (api.md 20). 사용자 말(chat_messages.input_type)에 저장한다 (2026-11-23) */
public enum InputType {
    /** 직접 입력 */
    TEXT,
    /** 빠른 답장 */
    QUICK,
    /** 누르고 말하기 (STT 글자) */
    VOICE
}
