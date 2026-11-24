package com.capsule.dialogue.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 음성으로 읽을 글 다듬기 (api.md 28 '음성' 읽기용 정리). 글은 그대로, TTS에 넘길 조각만 */
class SpeechTextTest {

    @Test
    void removesStageDirections() {
        assertThat(SpeechText.clean("(한숨 쉬며) 또 틀렸어?")).isEqualTo("또 틀렸어?");
        assertThat(SpeechText.clean("*고개를 끄덕인다* 좋아.")).isEqualTo("좋아.");
        assertThat(SpeechText.clean("[웃음] 알지.")).isEqualTo("알지.");
        assertThat(SpeechText.clean("알지 (작게) 고마워.")).isEqualTo("알지 고마워.");
    }

    @Test
    void removesEmojiAndJamoOnlyWords() {
        assertThat(SpeechText.clean("잘했어 👍😊")).isEqualTo("잘했어");
        assertThat(SpeechText.clean("ㅋㅋㅋ 진짜?")).isEqualTo("진짜?");
        assertThat(SpeechText.clean("힘들었구나 ㅠㅠ")).isEqualTo("힘들었구나");
        assertThat(SpeechText.clean("❤️")).isNull();
    }

    @Test
    void tidiesMarks() {
        assertThat(SpeechText.clean("좋아~~")).isEqualTo("좋아");
        assertThat(SpeechText.clean("진짜!!!")).isEqualTo("진짜!");
        assertThat(SpeechText.clean("뭐?!?!")).isEqualTo("뭐?!");
        assertThat(SpeechText.clean("음..... 글쎄")).isEqualTo("음... 글쎄");
        assertThat(SpeechText.clean("「이거 너무 어려워」… 그렇구나.")).isEqualTo("이거 너무 어려워… 그렇구나.");
    }

    @Test
    void nothingLeftMeansNoSpeech() {
        assertThat(SpeechText.clean("ㅋㅋㅋㅋ")).isNull();
        assertThat(SpeechText.clean("(웃음)")).isNull();
        assertThat(SpeechText.clean("...")).isNull();
        assertThat(SpeechText.clean(null)).isNull();
    }

    @Test
    void plainSentenceIsUntouched() {
        assertThat(SpeechText.clean("오늘 3시간 했어? 대단하다.")).isEqualTo("오늘 3시간 했어? 대단하다.");
    }
}
