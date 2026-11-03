package com.capsule.dialogue.internal.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 사용자 × 캐릭터 대화방. 세션을 넘어 남는 건 요약뿐 */
@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long userId;
    private long characterId;
    private String summary;
    private Instant summaryUpdatedAt;
    private Instant createdAt;

    /** 행은 ConversationRepository.insertIfAbsent로 만든다 (첫 대화 동시 요청 대비) */
    protected Conversation() {}

    public long id() {
        return id;
    }

    public String summary() {
        return summary;
    }

    public void updateSummary(String summary, Instant now) {
        this.summary = summary;
        this.summaryUpdatedAt = now;
    }
}
