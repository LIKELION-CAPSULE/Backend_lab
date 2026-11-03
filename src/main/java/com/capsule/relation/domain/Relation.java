package com.capsule.relation.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/** 행 생성·점수·시간 갱신은 RelationRepository의 SQL로만 한다 (동시 갱신에 안전하게). */
@Entity
@Table(name = "user_character_relations")
@DynamicUpdate // level만 올릴 때 relation_points를 읽은 값으로 덮어쓰지 않게
public class Relation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long userId;
    private long characterId;
    private int relationPoints;
    private int level;
    private int totalStudySeconds;
    private Instant firstMetAt;
    private Instant lastMetAt;

    protected Relation() {}

    public long id() {
        return id;
    }

    public int points() {
        return relationPoints;
    }

    public int level() {
        return level;
    }

    public int totalStudySeconds() {
        return totalStudySeconds;
    }

    /** 이미 오른 단계는 내리지 않는다 (DB 주석) */
    public void raiseLevel(int computed) {
        level = Math.max(level, computed);
    }
}
