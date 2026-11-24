package com.capsule.dialogue.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 음성 TTS 설정 (ADR 0034, api.md 28 '음성'). application.yml에 두지 않고 기본값을 여기 둔다 (공용 파일을 바꾸지 않게).
 * 운영에서 켜려면 환경 변수 CAPSULE_TTS_PROVIDER=openai (키는 LLM과 같은 OPENAI_API_KEY).
 *
 * @param provider fake(기본, 무음 · 비용 없음) · openai
 * @param model OpenAI 음성 모델. 이름 · 가격은 바꾸기 전에 공식 문서로 확인
 * @param voice 메이트별 목소리가 없을 때 쓰는 목소리
 * @param voices 메이트 id → 목소리 이름
 * @param instructions 말투 지시 (gpt-4o-mini-tts처럼 instructions를 받는 모델만). 비우면 보내지 않음
 * @param firstClauseMin 첫 조각을 쉼표에서도 자를 최소 글자 수 (첫 소리를 빨리 내려고)
 * @param waitAfterDone done을 보낸 뒤 남은 음성을 기다리는 한도. 넘으면 audio_end complete=false
 */
@ConfigurationProperties("capsule.tts")
public record TtsProperties(
        @DefaultValue("fake") String provider,
        @DefaultValue("gpt-4o-mini-tts") String model,
        @DefaultValue("coral") String voice,
        Map<Long, String> voices,
        @DefaultValue("") String instructions,
        @DefaultValue("6") int firstClauseMin,
        @DefaultValue("30s") java.time.Duration waitAfterDone) {

    public TtsProperties {
        voices = voices == null ? Map.of() : Map.copyOf(voices);
    }

    // TODO(judgment): J-10 메이트별 실제 목소리. 이제 메이트 데이터(characters.tts_voice, ADR 0036)가 먼저고, 이 값은 비었을 때만
    public String voiceFor(long mateId) {
        return voices.getOrDefault(mateId, voice);
    }
}
