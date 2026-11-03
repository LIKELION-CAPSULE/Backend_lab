package com.capsule.dialogue.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 사용자 × 캐릭터 대화방. 세션을 넘어 남는 건 요약뿐 */
@Entity
@Table(name = "conversations")
class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long userId;
    private long characterId;
    private String summary;
    private Instant summaryUpdatedAt;
    private Instant createdAt;

    protected Conversation() {}

    Conversation(long userId, long characterId, Instant now) {
        this.userId = userId;
        this.characterId = characterId;
        this.createdAt = now;
    }

    long id() {
        return id;
    }

    String summary() {
        return summary;
    }

    void updateSummary(String summary, Instant now) {
        this.summary = summary;
        this.summaryUpdatedAt = now;
    }
}
