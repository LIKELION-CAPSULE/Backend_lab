package com.capsule.dialogue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

/** 케이스 × 반복 한 번의 결과. transcript = TranscriptTurn JSON 배열 */
@Entity
@Table(name = "llm_eval_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvalResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long runId;
    private long caseId;
    private int repeatNo;
    private UUID traceId;

    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String transcript;

    private BigDecimal judgeScore;
    private String judgeNote;
    private Integer humanScore;
    private String humanNote;
    private int latencyMs;
    private BigDecimal costUsd;
    private String error;
    private Instant createdAt;

    public EvalResult(
            long runId,
            long caseId,
            int repeatNo,
            UUID traceId,
            String transcript,
            BigDecimal judgeScore,
            String judgeNote,
            int latencyMs,
            BigDecimal costUsd,
            String error,
            Instant now) {
        this.runId = runId;
        this.caseId = caseId;
        this.repeatNo = repeatNo;
        this.traceId = traceId;
        this.transcript = transcript;
        this.judgeScore = judgeScore;
        this.judgeNote = judgeNote;
        this.latencyMs = latencyMs;
        this.costUsd = costUsd;
        this.error = error;
        this.createdAt = now;
    }

    public void rate(Integer score, String note) {
        this.humanScore = score;
        this.humanNote = note;
    }
}
