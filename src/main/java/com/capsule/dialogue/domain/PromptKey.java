package com.capsule.dialogue.domain;

import java.util.Arrays;

/**
 * prompt_versions.prompt_key. 키마다 classpath:prompts/{key}.{format}을 처음 시작할 때 버전 1(ACTIVE)로 넣는다(PromptSeeder).
 */
public enum PromptKey {
    CHAT_SYSTEM("chat.system", "md", "답장(20) 시스템: 캐릭터·규칙·출력 형식. 캐릭터마다 같아서 프롬프트 캐시에 올라감"),
    CHAT_CONTEXT("chat.context", "md", "답장(20) 상황: 시각·닉네임·호감도·지난 요약·일정. 매번 바뀜(캐시 안 됨)"),
    SCENE_SYSTEM("scene.system", "md", "답장(20, SCENE): DB 캐릭터 설정(바이블·규칙·세계·표정)을 끼우는 틀. 캐시에 올라감"),
    FIXED_LINES("fixed.lines", "yml", "LLM 없이 쓰는 대사: 상태 반응(19) 고정 대사·감정, LLM 실패 때 대체 대사(20·22)"),
    REACTION_SYSTEM("reaction.system", "md", "상태 반응(19)을 LLM으로 만들 때 (capsule.dialogue.reaction-llm-triggers)"),
    SUMMARY_SYSTEM("summary.system", "md", "종료(22): 이번 대화를 지난 요약에 합치기 (J-08)"),
    CLOSING_SYSTEM("closing.system", "md", "종료(22): 완료 대사 (J-09)"),
    EVAL_JUDGE("eval.judge", "md", "실험실 평가 심사관 (LLM 채점)");

    private final String key;
    private final String format;
    private final String description;

    PromptKey(String key, String format, String description) {
        this.key = key;
        this.format = format;
        this.description = description;
    }

    public String key() {
        return key;
    }

    public String format() {
        return format;
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
