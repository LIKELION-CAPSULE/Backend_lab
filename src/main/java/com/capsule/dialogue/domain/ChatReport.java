package com.capsule.dialogue.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 답변 신고 (api.md 41). 신고한 사람 · 답장마다 한 번. 처리(status)는 베타 동안 운영자가 DB에서 */
@Entity
@Table(name = "chat_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long userId;
    private long messageId;
    private String reason;
    private String detail;
    private String status;
    private Instant createdAt;

    public ChatReport(long userId, long messageId, String reason, String detail, Instant now) {
        this.userId = userId;
        this.messageId = messageId;
        this.reason = reason;
        this.detail = detail;
        this.status = "OPEN";
        this.createdAt = now;
    }
}
