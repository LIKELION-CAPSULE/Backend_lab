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

/** 프롬프트 한 버전. 본문은 바꾸지 않는다: 고치려면 새 버전을 만든다 (무엇이 언제 바뀌었는지 남도록) */
@Entity
@Table(name = "prompt_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PromptVersion {

    public static final String DRAFT = "DRAFT";
    public static final String ACTIVE = "ACTIVE";
    public static final String ARCHIVED = "ARCHIVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String promptKey;
    private Long characterId;
    private int version;
    private String status;
    private String body;
    private String note;
    private Instant createdAt;
    private Instant activatedAt;

    public PromptVersion(String promptKey, Long characterId, int version, String body, String note, Instant now) {
        this.promptKey = promptKey;
        this.characterId = characterId;
        this.version = version;
        this.status = DRAFT;
        this.body = body;
        this.note = note;
        this.createdAt = now;
    }

    public void activate(Instant now) {
        this.status = ACTIVE;
        this.activatedAt = now;
    }

    public void archive() {
        this.status = ARCHIVED;
    }
}
