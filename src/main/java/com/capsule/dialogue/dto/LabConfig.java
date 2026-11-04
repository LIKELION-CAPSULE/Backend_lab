package com.capsule.dialogue.dto;

import java.util.List;
import java.util.Map;

/**
 * 실험 조건. 실험 대화방·평가 실행·한 번 실행(playground)이 같이 쓴다. null인 칸은 설정(capsule.llm)과 기본값.
 *
 * @param nickname · relationLevel · summary · memories 실제 API에서 DB가 채우는 값(닉네임·호감도 단계·지난 대화 요약·일정)을 대신한다
 * @param pinnedPrompts 프롬프트 키 → 고정할 prompt_versions.id. ACTIVE가 아닌 DRAFT를 켜기 전에 시험할 때
 */
public record LabConfig(
        String nickname,
        Integer relationLevel,
        String summary,
        List<String> memories,
        String provider,
        String model,
        Double temperature,
        Integer maxTokens,
        Map<String, Long> pinnedPrompts,
        EngineOptions engine) {

    public static final LabConfig EMPTY = new LabConfig(null, null, null, null, null, null, null, null, null, null);

    public String nicknameOrDefault() {
        return nickname == null || nickname.isBlank() ? "테스터" : nickname;
    }

    public int relationLevelOrDefault() {
        return relationLevel == null ? 1 : relationLevel;
    }

    public List<String> memoriesOrEmpty() {
        return memories == null ? List.of() : memories;
    }

    public Map<String, Long> pinnedOrEmpty() {
        return pinnedPrompts == null ? Map.of() : pinnedPrompts;
    }

    public EngineOptions engineOrDefault() {
        return engine == null ? EngineOptions.DEFAULT : engine;
    }
}
