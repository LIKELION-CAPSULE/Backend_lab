package com.capsule.relation.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 공부 종료 정산 때의 레벨 (리포트를 다시 볼 때 같은 값). 세션마다 한 번 */
@Entity
@Table(name = "relation_settlements")
@IdClass(RelationSettlement.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RelationSettlement {

    public record Key(Long relationId, Long sessionId) implements Serializable {
        public Key() {
            this(null, null);
        }
    }

    @Id
    private Long relationId;

    @Id
    private Long sessionId;

    private int presentSeconds;
    private int levelBefore;
    private int levelAfter;
    private Instant createdAt;

    public RelationSettlement(
            long relationId, long sessionId, int presentSeconds, int levelBefore, int levelAfter, Instant createdAt) {
        this.relationId = relationId;
        this.sessionId = sessionId;
        this.presentSeconds = presentSeconds;
        this.levelBefore = levelBefore;
        this.levelAfter = levelAfter;
        this.createdAt = createdAt;
    }
}
