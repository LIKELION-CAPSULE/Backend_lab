package com.capsule.dialogue.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 흘러 들어오는 답장을 음성 조각으로 자르기 (api.md 28 '음성', ADR 0034) */
class TtsSegmenterTest {

    @Test
    void cutsAtSentenceEndsAsTextGrows() {
        TtsSegmenter seg = new TtsSegmenter(6);
        List<String> out = new ArrayList<>();
        out.addAll(seg.feed("알지"));
        assertThat(out).isEmpty(); // 아직 문장이 안 끝남
        out.addAll(seg.feed("알지. 그리핀"));
        assertThat(out).containsExactly("알지.");
        out.addAll(seg.feed("알지. 그리핀도르 그 녀석! 퀴디치"));
        assertThat(out).containsExactly("알지.", "그리핀도르 그 녀석!");
        assertThat(seg.flush("알지. 그리핀도르 그 녀석! 퀴디치에서 맨날 붙는 애.")).containsExactly("퀴디치에서 맨날 붙는 애.");
        assertThat(seg.flush("알지. 그리핀도르 그 녀석! 퀴디치에서 맨날 붙는 애.")).isEmpty();
    }

    @Test
    void firstPieceAlsoCutsAtACommaWhenLongEnough() {
        TtsSegmenter seg = new TtsSegmenter(6);
        assertThat(seg.feed("응, 그")).isEmpty(); // 쉼표까지 2글자: 너무 짧다
        assertThat(seg.feed("응, 그거 어렵긴 하지, 근데")).containsExactly("응, 그거 어렵긴 하지,");
        // 첫 조각 뒤로는 쉼표에서 자르지 않는다
        assertThat(seg.feed("응, 그거 어렵긴 하지, 근데 천천히 하면, 돼")).isEmpty();
    }

    @Test
    void keepsTrailingMarksAndQuotesAndWaitsWhileMarksMayContinue() {
        TtsSegmenter seg = new TtsSegmenter(6);
        assertThat(seg.feed("뭐...")).isEmpty(); // 점이 더 올 수 있다
        assertThat(seg.feed("뭐... 진짜?\" 그래")).containsExactly("뭐...", "진짜?\"");
    }

    @Test
    void skipsPiecesWithoutWords() {
        TtsSegmenter seg = new TtsSegmenter(6);
        assertThat(seg.feed("... 좋아. ")).containsExactly("좋아.");
        assertThat(seg.flush("... 좋아. !!")).isEmpty();
    }

    @Test
    void lineThatCameAtOnceIsStillSplit() {
        // 대체 · 거절 대사는 reply 없이 done으로 한 번에 온다: 그래도 문장마다 나눠 첫 소리를 빨리
        TtsSegmenter seg = new TtsSegmenter(6);
        assertThat(seg.flush("미안, 잠깐 딴생각했어. 다시 말해줄래?")).containsExactly("미안, 잠깐 딴생각했어.", "다시 말해줄래?");
    }

    @Test
    void doesNotCutInsideStageDirections() {
        // 지문 안의 마침표에서 자르면 반쪽 괄호가 남아 읽기용 정리가 지문을 못 뺀다
        TtsSegmenter seg = new TtsSegmenter(6);
        assertThat(seg.feed("(한숨을 쉬며. 웃는다) 알지. 그")).containsExactly("(한숨을 쉬며. 웃는다) 알지.");
        assertThat(seg.feed("(한숨을 쉬며. 웃는다) 알지. *눈을 감는다. 잠깐* 좋아! 응")).containsExactly("*눈을 감는다. 잠깐* 좋아!");
    }
}
