package com.capsule.content.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 친밀도 해금 콘텐츠 (character_contents, 2026-11-23 5단계): 표정 · 보이스 대사.
 * 이름이 CharacterContent면 dialogue의 CharacterContentRepository(캐릭터 설정)와 빈 이름이 겹쳐서 Unlock을 붙임
 */
@Entity
@Table(name = "character_contents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UnlockContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long characterId;
    /** EXPRESSION · VOICE_LINE */
    private String type;

    private String title;
    private String body;
    private String assetUrl;
    private String expressionName;
    private int unlockStage;
    private boolean isActive;
}
