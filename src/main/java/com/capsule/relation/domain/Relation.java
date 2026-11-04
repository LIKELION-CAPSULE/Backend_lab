package com.capsule.relation.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** 행 생성·점수·시간·출석 갱신은 RelationRepository의 SQL로만 한다 (동시 갱신에 안전하게). 여기는 읽기만. */
@Entity
@Table(name = "user_character_relations")
public class Relation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long userId;
    private long characterId;
    private int relationPoints;
    private int level;
    private int totalStudySeconds;
    private int totalPresentSeconds;
    private int streakDays;
    private LocalDate lastStudyDate;
    private Instant firstMetAt;
    private Instant lastMetAt;

    protected Relation() {}

    public long id() {
        return id;
    }

    public int points() {
        return relationPoints;
    }

    public int totalStudySeconds() {
        return totalStudySeconds;
    }

    public int totalPresentSeconds() {
        return totalPresentSeconds;
    }

    public int streakDays() {
        return streakDays;
    }

    public LocalDate lastStudyDate() {
        return lastStudyDate;
    }
}
