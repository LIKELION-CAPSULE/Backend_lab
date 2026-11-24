package com.capsule.dialogue.client;

import java.util.function.Consumer;

/** 글 → 음성 (ADR 0034). 공급자마다 구현 하나: 가짜(기본) · OpenAI */
public interface TtsClient {

    String FORMAT = "mp3";

    String name();

    /**
     * 글 하나를 음성(mp3)으로 바꿔 받는 대로 onChunk에 넘긴다 (다 받을 때까지 막힘). 실패하면 예외
     *
     * @param speed 0.25~4.0, 1이 보통
     */
    void speak(Speech speech, Consumer<byte[]> onChunk);

    /** @param model 메이트 목소리의 모델 (ADR 0036). null이면 운영 설정 capsule.tts.model */
    record Speech(String text, String voice, double speed, String instructions, String model) {

        public Speech(String text, String voice, double speed, String instructions) {
            this(text, voice, speed, instructions, null);
        }
    }
}
