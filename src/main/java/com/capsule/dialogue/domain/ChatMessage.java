package com.capsule.dialogue.domain;

import jakarta.persistence.Column;
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

/** 대화방 메시지. 연결이 끝나면 흐름 카드에 넣고 recappedAt만 채운다 (32 대화방 기록으로 남음) */
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

    @Column(name = "connection_id") // 2026-11-23 세션 → 연결. 자바 이름은 그대로
    private long sessionId;

    private UUID requestId;
    private String sender;
    private String content;
    private String signal; // 사용자 말의 종류 (SignalType 이름). 답장을 저장할 때 채운다
    private Instant createdAt;

    /** 흐름 카드에 넣은 시각. null이면 아직 (2026-11-23: 지우지 않고 대화방 기록으로 남김) */
    private Instant recappedAt;

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
