package com.capsule.dialogue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** LLM 호출 한 번의 기록 (성공·실패 모두). 지우지 않고 쌓는다: 비용 근거이자 품질·지연 분석 자료 */
@Entity
@Table(name = "llm_usages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LlmUsage {

    public static final String OK = "OK";
    public static final String ERROR = "ERROR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long sessionId;
    private String purpose;
    private String source;
    private UUID traceId;
    private String provider;
    private String model;
    private String status;
    private String error;
    private String stopReason;
    private int inputTokens;
    private int outputTokens;
    private int cacheReadTokens;
    private int cacheWriteTokens;
    private BigDecimal costUsd;
    private int latencyMs;
    private Integer ttftMs;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<Long> promptVersionIds;

    @Column(columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String request;

    private String response;
    private Instant createdAt;

    /** 만드는 곳은 LlmGateway 한 곳. error가 null이면 성공 */
    public LlmUsage(
            Long userId,
            Long sessionId,
            String purpose,
            String source,
            UUID traceId,
            String provider,
            String model,
            String error,
            String stopReason,
            int inputTokens,
            int outputTokens,
            int cacheReadTokens,
            int cacheWriteTokens,
            BigDecimal costUsd,
            int latencyMs,
            Integer ttftMs,
            List<Long> promptVersionIds,
            String request,
            String response,
            Instant now) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.purpose = purpose;
        this.source = source;
        this.traceId = traceId;
        this.provider = provider;
        this.model = model;
        this.status = error == null ? OK : ERROR;
        this.error = error;
        this.stopReason = stopReason;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cacheReadTokens = cacheReadTokens;
        this.cacheWriteTokens = cacheWriteTokens;
        this.costUsd = costUsd;
        this.latencyMs = latencyMs;
        this.ttftMs = ttftMs;
        this.promptVersionIds = promptVersionIds;
        this.request = request;
        this.response = response;
        this.createdAt = now;
    }
}
