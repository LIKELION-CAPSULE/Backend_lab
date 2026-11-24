package com.capsule.dialogue.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.capsule.content.api.ContentTypes.VoiceInfo;
import com.capsule.content.api.VoiceQuery;
import com.capsule.dialogue.client.TtsClient;
import com.capsule.dialogue.client.TtsClient.Speech;
import com.capsule.dialogue.config.TtsProperties;
import com.capsule.user.api.UserTypes.SettingsInfo;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/** 메이트 목소리 (ADR 0036): 메이트 데이터가 먼저, 빈 칸은 운영 설정. 말 빠르기는 말투 지시 뒤에 */
class VoiceStreamsTest {

    private final List<Speech> spoken = new CopyOnWriteArrayList<>();
    private final TtsClient tts = new TtsClient() {
        @Override
        public String name() {
            return "test";
        }

        @Override
        public void speak(Speech speech, java.util.function.Consumer<byte[]> onChunk) {
            spoken.add(speech);
            onChunk.accept(new byte[] {1});
        }
    };
    private final TtsProperties props = new TtsProperties(
            "fake", "gpt-4o-mini-tts", "coral", Map.of(2L, "alloy"), "또박또박 말한다.", 6, Duration.ofSeconds(5));

    private Speech speak(VoiceQuery mates, long mateId, String speed) {
        spoken.clear();
        VoiceStreams streams = new VoiceStreams(
                tts, props, userId -> new SettingsInfo("NORMAL", true, 80, speed, true, "BASIC", "M"), mates);
        VoiceStream v = streams.open(1, mateId, chunk -> true);
        v.finish("안녕.");
        v.await(Duration.ofSeconds(5));
        assertThat(spoken).hasSize(1);
        return spoken.get(0);
    }

    @Test
    void theMatesVoiceModelAndInstructionsAreUsed() {
        Speech s = speak(id -> Optional.of(new VoiceInfo("nova", "tts-1", "다정하게 말한다.")), 1, "FAST");
        assertThat(s.voice()).isEqualTo("nova");
        assertThat(s.model()).isEqualTo("tts-1");
        assertThat(s.instructions()).isEqualTo("다정하게 말한다. 조금 빠르게 말한다.");
        assertThat(s.speed()).isEqualTo(1.2);
    }

    @Test
    void emptyFieldsFallBackToTheSettings() {
        // 목소리를 안 정한 메이트: 설정의 메이트별 목소리(voices) → 기본 목소리, 모델은 null(클라이언트가 설정값), 말투는 설정
        Speech s = speak(id -> Optional.of(new VoiceInfo(null, " ", null)), 2, "NORMAL");
        assertThat(s.voice()).isEqualTo("alloy");
        assertThat(s.model()).isNull();
        assertThat(s.instructions()).isEqualTo("또박또박 말한다.");

        Speech unknown = speak(id -> Optional.empty(), 3, "SLOW");
        assertThat(unknown.voice()).isEqualTo("coral");
        assertThat(unknown.instructions()).isEqualTo("또박또박 말한다. 조금 천천히 말한다.");
    }
}
