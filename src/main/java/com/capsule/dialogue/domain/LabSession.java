package com.capsule.dialogue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.ColumnTransformer;

/** 실험실 대화방. config = LabConfig JSON, state = ENGINE 파이프라인 상태 JSON */
@Entity
@Table(name = "llm_lab_sessions")
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

    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String state;

    private Instant createdAt;
    private Instant updatedAt;

    protected LabSession() {}

    public LabSession(String title, long characterId, String pipeline, String config, Instant now) {
        this.title = title;
        this.characterId = characterId;
        this.pipeline = pipeline;
        this.config = config;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 파이프라인이 바뀌면 엔진 상태는 버린다 */
    public void update(String title, String pipeline, String config, Instant now) {
        if (title != null) this.title = title;
        if (pipeline != null && !pipeline.equals(this.pipeline)) {
            this.pipeline = pipeline;
            this.state = null;
        }
        if (config != null) this.config = config;
        this.updatedAt = now;
    }

    public void saveState(String state, Instant now) {
        this.state = state;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public String title() {
        return title;
    }

    public long characterId() {
        return characterId;
    }

    public String pipeline() {
        return pipeline;
    }

    public String config() {
        return config;
    }

    public String state() {
        return state;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
