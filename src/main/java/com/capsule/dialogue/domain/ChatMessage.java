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

    /** USER만: TEXT · QUICK · VOICE (2026-11-23 전 행은 null) */
    private String inputType;

    /** CHARACTER만: 이 답장의 대화 모드 BASIC · ADVANCED */
    private String mode;

    /** CHARACTER만: 사용자 평가 UP · DOWN (api.md 40). null이면 없음 */
    private String rating;

    /** 신고로 가린 시각 (api.md 41). 대화방 · 이전 대화에서 빠진다 */
    private Instant hiddenAt;

    public ChatMessage(
            long conversationId, long sessionId, UUID requestId, String sender, String content, Instant now) {
        this.conversationId = conversationId;
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.sender = sender;
        this.content = content;
        this.createdAt = now;
    }

    /** 사용자 말 (inputType을 같이 저장) */
    public static ChatMessage question(
            long conversationId, long sessionId, UUID requestId, String content, String inputType, Instant now) {
        ChatMessage m = new ChatMessage(conversationId, sessionId, requestId, USER, content, now);
        m.inputType = inputType;
        return m;
    }

    /** 캐릭터 답장 (대화 모드를 같이 저장) */
    public static ChatMessage reply(
            long conversationId, long sessionId, UUID requestId, String content, String mode, Instant now) {
        ChatMessage m = new ChatMessage(conversationId, sessionId, requestId, CHARACTER, content, now);
        m.mode = mode;
        return m;
    }

    /** 평가를 덮어쓴다. null이면 취소 */
    public void rate(String rating) {
        this.rating = rating;
    }

    /** 신고로 가린다. 이미 가렸으면 그 시각 그대로 */
    public void hide(Instant at) {
        if (hiddenAt == null) hiddenAt = at;
    }

    public boolean hidden() {
        return hiddenAt != null;
    }
}
