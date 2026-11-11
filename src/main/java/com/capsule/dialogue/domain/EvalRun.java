package com.capsule.dialogue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 평가 실행 하나: 케이스 묶음 × 반복을 한 조건(config = LabConfig JSON)으로 */
@Entity
@Table(name = "llm_eval_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvalRun {

    public static final String RUNNING = "RUNNING";
    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String label;
    private String pipeline;

    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String config;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<Long> caseIds;

    private int repeats;
    private boolean judge;
    private String status;
    private Instant createdAt;
    private Instant finishedAt;

    public EvalRun(
            String label, String pipeline, String config, List<Long> caseIds, int repeats, boolean judge, Instant now) {
        this.label = label;
        this.pipeline = pipeline;
        this.config = config;
        this.caseIds = caseIds;
        this.repeats = repeats;
        this.judge = judge;
        this.status = RUNNING;
        this.createdAt = now;
    }

    public void finish(String status, Instant now) {
        this.status = status;
        this.finishedAt = now;
    }
}
