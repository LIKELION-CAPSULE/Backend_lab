package com.capsule.dialogue.dto;

/** 평가 결과의 대화 한 턴. 실패하면 reply null, error에 이유 (그 뒤 턴은 돌리지 않음) */
public record TranscriptTurn(
        String user, String reply, String emotion, Integer affinity, long latencyMs, String error) {}
