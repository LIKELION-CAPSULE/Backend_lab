package com.capsule.dialogue.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.capsule.dialogue.client.TtsClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** 한 답장의 음성: 조각 순서 = seq 순서, 실패하면 그 뒤는 안 보냄 (ADR 0034) */
class VoiceStreamTest {

    final List<VoiceStream.AudioChunk> sent = new CopyOnWriteArrayList<>();

    /** 받은 글을 그대로 바이트로 돌려주는 TTS. 앞 조각은 일부러 늦게 끝낸다 (순서가 지켜지는지) */
    static class EchoTts implements TtsClient {
        final List<Speech> asked = new CopyOnWriteArrayList<>();

        @Override
        public String name() {
            return "echo";
        }

        @Override
        public void speak(Speech speech, Consumer<byte[]> onChunk) {
            asked.add(speech);
            if (asked.size() == 1) sleep(100);
            byte[] all = speech.text().getBytes(StandardCharsets.UTF_8);
            onChunk.accept(all);
        }
    }

    @Test
    void piecesAreSpokenInOrderWithSeq() {
        EchoTts tts = new EchoTts();
        VoiceStream v = new VoiceStream(tts, "coral", 1.0, null, 6, sent::add);

        v.onText("알지.");
        v.onText("알지. 그리핀도르 그 녀석!");
        v.onText("알지. 그리핀도르 그 녀석! 퀴디치");
        v.finish("알지. 그리핀도르 그 녀석! 퀴디치에서 맨날 붙는 애.");

        VoiceStream.AudioEnd end = v.await(Duration.ofSeconds(5));
        assertThat(end.complete()).isTrue();
        assertThat(end.chunks()).isEqualTo(3);
        assertThat(sent).extracting(VoiceStream.AudioChunk::seq).containsExactly(0, 1, 2);
        assertThat(sent)
                .extracting(c -> new String(Base64.getDecoder().decode(c.data()), StandardCharsets.UTF_8))
                .containsExactly("알지.", "그리핀도르 그 녀석!", "퀴디치에서 맨날 붙는 애.");
        assertThat(tts.asked).allSatisfy(s -> assertThat(s.voice()).isEqualTo("coral"));
    }

    @Test
    void failureStopsTheRestAndEndIsIncomplete() {
        TtsClient failing = new TtsClient() {
            int n;

            @Override
            public String name() {
                return "failing";
            }

            @Override
            public void speak(Speech speech, Consumer<byte[]> onChunk) {
                if (++n == 2) throw new IllegalStateException("TTS 실패");
                onChunk.accept(new byte[] {1});
            }
        };
        VoiceStream v = new VoiceStream(failing, "coral", 1.0, null, 6, sent::add);
        v.finish("하나. 둘. 셋.");
        v.onText(null);

        VoiceStream.AudioEnd end = v.await(Duration.ofSeconds(5));
        assertThat(end.complete()).isFalse();
        assertThat(sent).hasSize(1);
    }

    @Test
    void tooSlowIsIncompleteAndLateChunksAreDropped() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        TtsClient slow = new TtsClient() {
            @Override
            public String name() {
                return "slow";
            }

            @Override
            public void speak(Speech speech, Consumer<byte[]> onChunk) {
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                onChunk.accept(new byte[] {1});
            }
        };
        VoiceStream v = new VoiceStream(slow, "coral", 1.0, null, 6, sent::add);
        v.finish("늦는 말.");

        assertThat(v.await(Duration.ofMillis(100)).complete()).isFalse();
        release.countDown();
        Thread.sleep(200);
        assertThat(sent).isEmpty(); // audio_end 뒤에 audio가 오지 않게
    }

    @Test
    void cancelOrAClosedStreamStopsTheRestWithoutCallingTts() {
        EchoTts tts = new EchoTts();
        // 받는 쪽이 첫 조각에서 끊김 → 남은 조각은 TTS도 부르지 않는다 (비용)
        VoiceStream v = new VoiceStream(tts, "coral", 1.0, null, 6, chunk -> false);
        v.finish("하나. 둘. 셋.");
        assertThat(v.await(Duration.ofSeconds(5)).complete()).isFalse();
        assertThat(tts.asked).hasSize(1);

        EchoTts tts2 = new EchoTts();
        VoiceStream cancelled = new VoiceStream(tts2, "coral", 1.0, null, 6, sent::add);
        cancelled.cancel();
        cancelled.finish("하나. 둘.");
        assertThat(cancelled.await(Duration.ofSeconds(5))).isEqualTo(new VoiceStream.AudioEnd(0, false));
        assertThat(tts2.asked).isEmpty();
        assertThat(sent).isEmpty();
    }

    @Test
    void ttsGetsTheCleanedTextAndSkipsPiecesWithNothingToSay() {
        EchoTts tts = new EchoTts();
        VoiceStream v = new VoiceStream(tts, "coral", 1.0, null, 6, sent::add);
        v.finish("(한숨) 또 틀렸어?! ㅋㅋㅋ. 다시 해 보자~");

        assertThat(v.await(Duration.ofSeconds(5)).complete()).isTrue();
        assertThat(tts.asked).extracting(TtsClient.Speech::text).containsExactly("또 틀렸어?!", "다시 해 보자");
        assertThat(sent).extracting(VoiceStream.AudioChunk::seq).containsExactly(0, 1); // 건너뛴 조각은 seq를 쓰지 않음
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
