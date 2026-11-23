package com.capsule.dialogue.service;

import com.capsule.dialogue.client.TtsClient;
import com.capsule.dialogue.client.TtsClient.Speech;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 한 답장의 음성 (api.md 28 '음성', ADR 0034). 글이 오는 대로 조각을 잘라 TTS에 넘기고, 받은 음성을 순서대로 sink에 보낸다.
 * 조각은 한 줄로(앞 조각이 끝나야 다음) 처리해 seq 순서 = 재생 순서.
 * 닫히면(실패 · 취소 · 기다림 초과) 더 보내지 않는다: "닫혔는지 보기 → seq → 보내기"를 잠금 하나로 묶어 audio_end 뒤에 audio가 오지 않게
 */
public class VoiceStream {

    private static final Logger log = LoggerFactory.getLogger(VoiceStream.class);
    private static final ExecutorService THREADS = Executors.newVirtualThreadPerTaskExecutor();

    /** SSE audio 이벤트 data */
    public record AudioChunk(int seq, String format, String data) {}

    /** SSE audio_end 이벤트 data */
    public record AudioEnd(int chunks, boolean complete) {}

    private final TtsClient tts;
    private final String voice;
    private final double speed;
    private final String instructions;
    private final TtsSegmenter segmenter;
    /** 조각 보내기. false면 보내지 못함(연결이 끊김 등) → 음성을 멈춘다 */
    private final Predicate<AudioChunk> sink;

    /** 보내기 잠금: closed · failed · seq는 이 잠금 안에서만 */
    private final Object sendLock = new Object();

    private boolean closed;
    private boolean failed;
    private int seq;
    private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);

    public VoiceStream(
            TtsClient tts,
            String voice,
            double speed,
            String instructions,
            int firstClauseMin,
            Predicate<AudioChunk> sink) {
        this.tts = tts;
        this.voice = voice;
        this.speed = speed;
        this.instructions = instructions;
        this.segmenter = new TtsSegmenter(firstClauseMin);
        this.sink = sink;
    }

    /** 지금까지의 답장 글 전체 (28 reply). 끝난 문장이 있으면 음성으로 넘긴다 */
    public synchronized void onText(String soFar) {
        for (String piece : segmenter.feed(soFar)) enqueue(piece);
    }

    /** 최종 답장(done.content): 남은 글을 넘긴다. 대체 대사 · 거절 대사처럼 reply 없이 온 글도 여기서 전부 */
    public synchronized void finish(String finalText) {
        for (String piece : segmenter.flush(finalText)) enqueue(piece);
    }

    /** 멈춘다 (오류 · 다른 답장으로 바뀜). 이미 보내는 중인 조각도 더 보내지 않는다 */
    public void cancel() {
        synchronized (sendLock) {
            closed = true;
            failed = true;
        }
    }

    /** 남은 음성을 다 보낼 때까지 기다린다 (최대 wait). 그 뒤로는 닫혀 더 보내지 않는다. 다 보냈으면 complete=true */
    public AudioEnd await(Duration wait) {
        CompletableFuture<Void> last;
        synchronized (this) {
            last = tail;
        }
        boolean done = true;
        try {
            last.get(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.info("음성을 {} 안에 다 못 보냄: {}", wait, e.toString());
            done = false;
        }
        synchronized (sendLock) {
            closed = true;
            return new AudioEnd(seq, done && !failed);
        }
    }

    private void enqueue(String piece) {
        tail = tail.thenRunAsync(() -> speak(piece), THREADS);
    }

    private void speak(String piece) {
        synchronized (sendLock) {
            if (closed) return; // 남은 조각은 TTS를 부르지 않는다 (비용)
        }
        try {
            tts.speak(new Speech(piece, voice, speed, instructions), this::send);
        } catch (RuntimeException e) {
            synchronized (sendLock) {
                failed = true;
                closed = true;
            }
            log.warn("음성 만들기 실패 ({}): {}", tts.name(), e.toString());
        }
    }

    private void send(byte[] bytes) {
        if (bytes.length == 0) return;
        String data = Base64.getEncoder().encodeToString(bytes);
        synchronized (sendLock) {
            if (closed) return;
            if (!sink.test(new AudioChunk(seq, TtsClient.FORMAT, data))) {
                // 받을 사람이 없다 (연결 끊김): 남은 음성은 만들지 않는다
                closed = true;
                failed = true;
                return;
            }
            seq++;
        }
    }
}
