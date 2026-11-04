package com.capsule.dialogue.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 지난 세션 하나의 흐름 카드 (공부 종료 때 한 번 쓰고 고치지 않음. 최근 몇 장만 남김) */
@Entity
@Table(name = "conversation_recaps")
public class ConversationRecap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long conversationId;
    private long sessionId;
    private String content;
    private Instant createdAt;

    protected ConversationRecap() {}

    public ConversationRecap(long conversationId, long sessionId, String content, Instant now) {
        this.conversationId = conversationId;
        this.sessionId = sessionId;
        this.content = content;
        this.createdAt = now;
    }

    public String content() {
        return content;
    }
}
