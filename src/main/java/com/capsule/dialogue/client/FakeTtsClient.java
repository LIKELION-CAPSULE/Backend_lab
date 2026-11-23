package com.capsule.dialogue.client;

import java.util.function.Consumer;

/**
 * 가짜 음성 (기본, 비용 없음). 글자 수에 비례하는 길이의 무음 mp3를 돌려준다: 프론트가 실제로 재생 · 이어 붙이기를 시험할 수 있게.
 * MPEG-1 Layer III 128kbps 44.1kHz 모노 프레임(417바이트, 약 26ms). 본문이 0이라 소리가 없다
 */
public class FakeTtsClient implements TtsClient {

    private static final byte[] SILENT_FRAME = silentFrame();
    /** 글자당 프레임 수 (약 0.1초) · 한 번에 최대 (약 5초) */
    private static final int FRAMES_PER_CHAR = 4;

    private static final int MAX_FRAMES = 200;

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public void speak(Speech speech, Consumer<byte[]> onChunk) {
        int frames = Math.max(1, Math.min(MAX_FRAMES, speech.text().strip().length() * FRAMES_PER_CHAR));
        byte[] audio = new byte[frames * SILENT_FRAME.length];
        for (int i = 0; i < frames; i++) {
            System.arraycopy(SILENT_FRAME, 0, audio, i * SILENT_FRAME.length, SILENT_FRAME.length);
        }
        onChunk.accept(audio);
    }

    private static byte[] silentFrame() {
        byte[] f = new byte[417]; // 144 * 128000 / 44100
        f[0] = (byte) 0xFF;
        f[1] = (byte) 0xFB; // MPEG-1, Layer III, CRC 없음
        f[2] = (byte) 0x90; // 128kbps, 44.1kHz, 패딩 없음
        f[3] = (byte) 0xC0; // 모노
        return f;
    }
}
