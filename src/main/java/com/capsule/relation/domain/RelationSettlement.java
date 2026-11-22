package com.capsule.relation.domain;

import jakarta.persistence.Column;
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
    @Column(name = "connection_id") // 2026-11-23 세션 → 연결. 자바 이름은 그대로
    private Long sessionId;

    private int presentSeconds;
    private int levelBefore;
    private int levelAfter;
    // 친밀도 (2026-11-23 5단계)
    private int creditedSeconds;
    private int stageBefore;
    private int stageAfter;
    private Instant createdAt;

    public RelationSettlement(
            long relationId, long sessionId, int presentSeconds, int levelBefore, int levelAfter, Instant createdAt) {
        this(relationId, sessionId, presentSeconds, levelBefore, levelAfter, 0, 1, 1, createdAt);
    }

    public RelationSettlement(
            long relationId,
            long sessionId,
            int presentSeconds,
            int levelBefore,
            int levelAfter,
            int creditedSeconds,
            int stageBefore,
            int stageAfter,
            Instant createdAt) {
        this.relationId = relationId;
        this.sessionId = sessionId;
        this.presentSeconds = presentSeconds;
        this.levelBefore = levelBefore;
        this.levelAfter = levelAfter;
        this.creditedSeconds = creditedSeconds;
        this.stageBefore = stageBefore;
        this.stageAfter = stageAfter;
        this.createdAt = createdAt;
    }
}
