package com.capsule.dialogue.service;

import com.capsule.content.api.ContentTypes.VoiceInfo;
import com.capsule.content.api.VoiceQuery;
import com.capsule.dialogue.client.TtsClient;
import com.capsule.dialogue.config.TtsProperties;
import com.capsule.user.api.UserSettingsQuery;
import java.time.Duration;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 답장 음성 열기 (api.md 28 '음성', ADR 0034): 설정 voiceEnabled · 말 빠르기 · 메이트 목소리를 모아 VoiceStream을 만든다 */
@Component
@RequiredArgsConstructor
public class VoiceStreams {

    private final TtsClient tts;
    private final TtsProperties props;
    private final UserSettingsQuery settings;
    private final VoiceQuery mates;

    /** 설정 29 voiceEnabled (끄면 크레딧도 들지 않음) */
    public boolean enabled(long userId) {
        return settings.settings(userId).voiceEnabled();
    }

    /**
     * 목소리 · 모델 · 말투 지시는 메이트 데이터(ADR 0036, '목소리 듣기' 샘플과 같은 값). 비어 있는 칸은 운영 설정 capsule.tts.
     * 말 빠르기(29)는 말투 지시 뒤에 붙인다
     */
    public VoiceStream open(long userId, long mateId, Predicate<VoiceStream.AudioChunk> sink) {
        String speed = settings.settings(userId).voiceSpeed();
        VoiceInfo mate = mates.voice(mateId).orElse(NONE);
        String voice = blank(mate.voice()) ? props.voiceFor(mateId) : mate.voice();
        String base = blank(mate.instructions()) ? props.instructions() : mate.instructions();
        return new VoiceStream(
                tts,
                voice,
                blank(mate.model()) ? null : mate.model(),
                speed(speed),
                instructions(base, speed),
                props.firstClauseMin(),
                sink);
    }

    private static final VoiceInfo NONE = new VoiceInfo(null, null, null);

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    public Duration waitAfterDone() {
        return props.waitAfterDone();
    }

    /** 29 voiceSpeed → tts-1 계열 speed */
    static double speed(String voiceSpeed) {
        return switch (voiceSpeed == null ? "NORMAL" : voiceSpeed) {
            case "SLOW" -> 0.85;
            case "FAST" -> 1.2;
            default -> 1.0;
        };
    }

    /** speed를 받지 않는 모델(gpt-4o-mini-tts)은 말투 지시로 빠르기를 전한다 */
    static String instructions(String base, String voiceSpeed) {
        String pace = switch (voiceSpeed == null ? "NORMAL" : voiceSpeed) {
            case "SLOW" -> "조금 천천히 말한다.";
            case "FAST" -> "조금 빠르게 말한다.";
            default -> "";
        };
        String joined = ((base == null ? "" : base.strip()) + " " + pace).strip();
        return joined.isEmpty() ? null : joined;
    }
}
