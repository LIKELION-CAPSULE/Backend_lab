package com.capsule.dialogue.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** api.md 32 대화방 기록 (최신순) */
public record ChatRoomResponse(List<Message> messages, String nextCursor) {

    /**
     * @param sender USER · MATE
     * @param inputType TEXT · QUICK · VOICE. 사용자 말에만 (메이트 답장 · 2026-11-23 전 말은 null)
     * @param rating UP · DOWN. 메이트 답장에 내가 남긴 평가(40). 사용자 말은 늘 null
     */
    public record Message(
            UUID requestId,
            String sender,
            String content,
            String inputType,
            String rating,
            Instant createdAt,
            long connectionId) {}
}
