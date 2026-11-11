package com.capsule.dialogue.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 진행 중 세션의 메시지. 세션이 끝나면 요약에 합치고 지운다 */
@Entity
@Table(name = "chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    public static final String USER = "USER";
    public static final String CHARACTER = "CHARACTER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long conversationId;
    private long sessionId;
    private UUID requestId;
    private String sender;
    private String content;
    private String signal; // 사용자 말의 종류 (SignalType 이름). 답장을 저장할 때 채운다
    private Instant createdAt;

    public ChatMessage(
            long conversationId, long sessionId, UUID requestId, String sender, String content, Instant now) {
        this.conversationId = conversationId;
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.sender = sender;
        this.content = content;
        this.createdAt = now;
    }
}
