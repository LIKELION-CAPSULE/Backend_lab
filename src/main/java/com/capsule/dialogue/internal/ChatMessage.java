package com.capsule.dialogue.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 진행 중 세션의 메시지. 세션이 끝나면 요약에 합치고 지운다 */
@Entity
@Table(name = "chat_messages")
class ChatMessage {

    static final String USER = "USER";
    static final String CHARACTER = "CHARACTER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long conversationId;
    private long sessionId;
    private UUID requestId;
    private String sender;
    private String content;
    private Instant createdAt;

    protected ChatMessage() {}

    ChatMessage(long conversationId, long sessionId, UUID requestId, String sender, String content, Instant now) {
        this.conversationId = conversationId;
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.sender = sender;
        this.content = content;
        this.createdAt = now;
    }

    String sender() {
        return sender;
    }

    String content() {
        return content;
    }
}
