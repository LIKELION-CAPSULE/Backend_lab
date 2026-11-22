package com.capsule.dialogue.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 안전 위반 기록 (api.md 20 '안전'). 저장은 SafetyStrikeRepository.record (재전송 · 이어받기에도 한 번만) */
@Entity
@Table(name = "safety_strikes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SafetyStrike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long userId;
    private Long connectionId;
    private UUID requestId;
    private String category;
    private Instant createdAt;
}
