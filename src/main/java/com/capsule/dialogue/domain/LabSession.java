package com.capsule.dialogue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

/** 실험실 대화방. config = LabConfig JSON */
@Entity
@Table(name = "llm_lab_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private long characterId;
    private String pipeline;

    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String config;

    private Instant createdAt;
    private Instant updatedAt;

    public LabSession(String title, long characterId, String pipeline, String config, Instant now) {
        this.title = title;
        this.characterId = characterId;
        this.pipeline = pipeline;
        this.config = config;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String title, String pipeline, String config, Instant now) {
        if (title != null) this.title = title;
        if (pipeline != null) this.pipeline = pipeline;
        if (config != null) this.config = config;
        this.updatedAt = now;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }
}
