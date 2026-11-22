package com.capsule.user.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** beta_feedback 테이블 (api.md 42). RATING은 하루(오전 5시 기준) 1번 = uq_beta_feedback_rating */
@Entity
@Table(name = "beta_feedback")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BetaFeedback {

    public static final String RATING = "RATING";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long userId;
    private String kind;
    private Integer score;
    private String text;
    private Long connectionId;
    private LocalDate feedbackDay;
    private Instant createdAt;

    public BetaFeedback(
            long userId, String kind, Integer score, String text, Long connectionId, LocalDate day, Instant now) {
        this.userId = userId;
        this.kind = kind;
        this.score = score;
        this.text = text;
        this.connectionId = connectionId;
        this.feedbackDay = day;
        this.createdAt = now;
    }
}
