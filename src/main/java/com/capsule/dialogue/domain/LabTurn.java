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

/** 실험 대화 한 턴. 실패한 턴도 남긴다(error). 이 턴의 LLM 호출은 llm_usages.trace_id */
@Entity
@Table(name = "llm_lab_turns")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long labSessionId;
    private UUID traceId;
    private String userText;
    private String reply;
    private String emotion;
    private Integer affinity;
    private String error;
    private int latencyMs;
    private BigDecimal costUsd;

    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String trace;

    private Integer rating;
    private String ratingNote;
    private Instant createdAt;

    public LabTurn(
            long labSessionId,
            UUID traceId,
            String userText,
            String reply,
            String emotion,
            Integer affinity,
            String error,
            int latencyMs,
            BigDecimal costUsd,
            String trace,
            Instant now) {
        this.labSessionId = labSessionId;
        this.traceId = traceId;
        this.userText = userText;
        this.reply = reply;
        this.emotion = emotion;
        this.affinity = affinity;
        this.error = error;
        this.latencyMs = latencyMs;
        this.costUsd = costUsd;
        this.trace = trace;
        this.createdAt = now;
    }

    public void rate(Integer rating, String note) {
        this.rating = rating;
        this.ratingNote = note;
    }
}
