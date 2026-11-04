package com.capsule.dialogue.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 캐릭터가 사용자에 대해 아는 사실 한 줄 (최대 20개, MemoryKeeper가 관리) */
@Entity
@Table(name = "character_memories")
public class CharacterMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long userId;
    private long characterId;
    private String content;
    private UUID sourceRequestId;
    private Instant createdAt;
    private Instant updatedAt;

    protected CharacterMemory() {}

    public CharacterMemory(long userId, long characterId, String content, UUID sourceRequestId, Instant now) {
        this.userId = userId;
        this.characterId = characterId;
        this.content = content;
        this.sourceRequestId = sourceRequestId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public long id() {
        return id;
    }

    public String content() {
        return content;
    }

    public void rewrite(String content, UUID sourceRequestId, Instant now) {
        this.content = content;
        this.sourceRequestId = sourceRequestId;
        this.updatedAt = now;
    }
}
