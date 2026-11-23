package com.capsule.content.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 메이트 배경 (character_backgrounds, 2026-11-23, ADR 0032). 대화 흐름에 따라 결정 모델이 이 목록에서 고른다 */
@Entity
@Table(name = "character_backgrounds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterBackground {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long characterId;
    private String code;
    private String name;
    private String imageUrl;
    /** 언제 이 배경인지 (결정 모델 보기 설명) */
    private String guide;

    private boolean isDefault;
    private int sortOrder;
    private boolean isActive;
}
