package com.capsule.dialogue.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** api.md 32 대화방 기록 (최신순) */
public record ChatRoomResponse(List<Message> messages, String nextCursor) {

    /**
     * @param sender USER · MATE
     * @param inputType TEXT · QUICK · VOICE. 메시지 전송이 아직 받지 않아 늘 null
     * @param rating UP · DOWN. 답변 평가(P1) 전까지 늘 null
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
