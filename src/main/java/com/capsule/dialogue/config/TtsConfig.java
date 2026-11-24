package com.capsule.dialogue.config;

import com.capsule.dialogue.client.FakeTtsClient;
import com.capsule.dialogue.client.OpenAiTtsClient;
import com.capsule.dialogue.client.TtsClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 음성 공급자 고르기 (ADR 0034). 기본은 가짜라 비용이 들지 않는다 */
@Configuration
public class TtsConfig {

    @Bean
    public TtsClient ttsClient(TtsProperties tts, LlmProperties llm) {
        return switch (tts.provider()) {
            case "fake" -> new FakeTtsClient();
            case "openai" -> {
                // 켰는데 키가 없으면 매 턴 실패하므로 시작에서 막는다 (LLM 공급자와 같은 방식)
                if (!llm.openai().hasKey()) {
                    throw new IllegalStateException("capsule.tts.provider=openai인데 OPENAI_API_KEY가 없음");
                }
                yield new OpenAiTtsClient(llm, tts);
            }
            default -> throw new IllegalStateException("모르는 capsule.tts.provider: " + tts.provider());
        };
    }
}
