package com.capsule.dialogue.domain;

import java.util.Arrays;

/**
 * prompt_versions.prompt_key. 공통 키는 classpath:prompts/{key}.{format}을 처음 시작할 때 버전 1(ACTIVE)로 넣는다(PromptSeeder).
 * 캐릭터 전용 키(engine.*)는 시드가 없고 실험실에서 캐릭터별로 올린다.
 */
public enum PromptKey {
    CHAT_SYSTEM("chat.system", "md", false, "답장(20) 시스템: 캐릭터·규칙·출력 형식. 캐릭터마다 같아서 프롬프트 캐시에 올라감"),
    CHAT_CONTEXT("chat.context", "md", false, "답장(20) 상황: 시각·닉네임·호감도·지난 요약·일정. 매번 바뀜(캐시 안 됨)"),
    FIXED_LINES("fixed.lines", "yml", false, "LLM 없이 쓰는 대사: 상태 반응(19) 고정 대사·감정, LLM 실패 때 대체 대사(20·22)"),
    REACTION_SYSTEM("reaction.system", "md", false, "상태 반응(19)을 LLM으로 만들 때 (capsule.dialogue.reaction-llm-triggers)"),
    SUMMARY_SYSTEM("summary.system", "md", false, "종료(22): 이번 대화를 지난 요약에 합치기 (J-08)"),
    CLOSING_SYSTEM("closing.system", "md", false, "종료(22): 완료 대사 (J-09)"),
    EVAL_JUDGE("eval.judge", "md", false, "실험실 평가 심사관 (LLM 채점)"),
    ENGINE_CHARACTER("engine.character", "yml", true, "대화 엔진 캐릭터 파일 (character.yml: 수·비트·세계 지식·사건·고정 대사)"),
    ENGINE_ACTOR("engine.actor", "md", true, "대화 엔진 배우 (actor.md)"),
    ENGINE_DIRECTOR("engine.director", "md", true, "대화 엔진 감독 (director.md, {{MOVES}} 자리 필요)"),
    ENGINE_JUDGE("engine.judge", "md", true, "대화 엔진 심사 (judge.md)");

    private final String key;
    private final String format;
    private final boolean characterOnly;
    private final String description;

    PromptKey(String key, String format, boolean characterOnly, String description) {
        this.key = key;
        this.format = format;
        this.characterOnly = characterOnly;
        this.description = description;
    }

    public String key() {
        return key;
    }

    public String format() {
        return format;
    }

    /** true면 캐릭터마다 따로만 있다 (공통 버전·시드 없음) */
    public boolean characterOnly() {
        return characterOnly;
    }

    public String description() {
        return description;
    }

    public String seedPath() {
        return "prompts/" + key + "." + format;
    }

    public static PromptKey of(String key) {
        return Arrays.stream(values())
                .filter(k -> k.key.equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("모르는 프롬프트 키: " + key));
    }
}
