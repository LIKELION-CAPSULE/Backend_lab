package com.capsule.dialogue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import org.hibernate.annotations.ColumnTransformer;

/** 대화 엔진 상태 (세션 × 캐릭터). state는 engine.DialogueState의 JSON. 세션이 끝나면 지운다 */
@Entity
@Table(name = "dialogue_states")
public class DialogueStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long sessionId;
    private long characterId;
    private int engineVersion;

    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String state;

    @Version
    private long version;

    private Instant updatedAt;

    protected DialogueStateEntity() {}

    public DialogueStateEntity(long sessionId, long characterId, int engineVersion, String state, Instant now) {
        this.sessionId = sessionId;
        this.characterId = characterId;
        this.engineVersion = engineVersion;
        this.state = state;
        this.updatedAt = now;
    }

    public void update(int engineVersion, String state, Instant now) {
        this.engineVersion = engineVersion;
        this.state = state;
        this.updatedAt = now;
    }

    public int engineVersion() {
        return engineVersion;
    }

    public String state() {
        return state;
    }
}
