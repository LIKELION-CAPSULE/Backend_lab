package com.capsule.dialogue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.ColumnTransformer;

/** 평가 케이스(골든 대본). turns = 사용자 말 JSON 배열, context = LabConfig JSON(가상 사용자 정보) */
@Entity
@Table(name = "llm_eval_cases")
public class EvalCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private long characterId;

    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String turns;

    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String context;

    private String rubric;
    private Instant createdAt;

    protected EvalCase() {}

    public EvalCase(String name, long characterId, String turns, String context, String rubric, Instant now) {
        this.name = name;
        this.characterId = characterId;
        this.turns = turns;
        this.context = context;
        this.rubric = rubric;
        this.createdAt = now;
    }

    public void update(String name, long characterId, String turns, String context, String rubric) {
        this.name = name;
        this.characterId = characterId;
        this.turns = turns;
        this.context = context;
        this.rubric = rubric;
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public long characterId() {
        return characterId;
    }

    public String turns() {
        return turns;
    }

    public String context() {
        return context;
    }

    public String rubric() {
        return rubric;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
