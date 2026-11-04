package com.capsule.dialogue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 실험실: 대화 엔진 파일 네 개(character.yml · actor.md · director.md · judge.md)를 그 캐릭터의 새 버전으로 */
public record EnginePackRequest(
        @NotNull Long characterId,
        @NotBlank String characterYml,
        @NotBlank String actor,
        @NotBlank String director,
        @NotBlank String judge,
        String note,
        boolean activate) {}
