package com.capsule.dialogue.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReplyStreamTest {

    @Test
    void readsTheReplySoFarFromHalfFinishedJson() {
        assertThat(ReplyStream.partial("")).isNull();
        assertThat(ReplyStream.partial("{\"re")).isNull(); // 키가 아직 다 안 옴
        assertThat(ReplyStream.partial("{\"reply\": \"")).isEmpty();
        assertThat(ReplyStream.partial("{\"reply\": \"알지. 그리핀")).isEqualTo("알지. 그리핀");
        assertThat(ReplyStream.partial("{\"reply\":\"알지.\",\"emotion\":\"경")).isEqualTo("알지.");
        // 감정이 먼저 와도 된다
        assertThat(ReplyStream.partial("{\"emotion\":\"경멸\",\"reply\":\"흥")).isEqualTo("흥");
        assertThat(ReplyStream.partial("{\"emotion\":\"경멸\"")).isNull();
    }

    @Test
    void escapesAreDecodedAndAHalfEscapeWaits() {
        assertThat(ReplyStream.partial("{\"reply\":\"그 이름 \\\"볼드모트\\\"는")).isEqualTo("그 이름 \"볼드모트\"는");
        assertThat(ReplyStream.partial("{\"reply\":\"줄\\n바꿈")).isEqualTo("줄\n바꿈");
        assertThat(ReplyStream.partial("{\"reply\":\"끝에 \\")).isEqualTo("끝에 ");
        assertThat(ReplyStream.partial("{\"reply\":\"\\uAC00\\uAC")).isEqualTo("가");
    }

    @Test
    void plainTextAndCodeFencesToo() {
        // 글만 쓰는 답(기본 형식)은 그 글. 옛 습관으로 뒤에 붙는 {"emotion"} JSON 앞까지
        assertThat(ReplyStream.partial("흥, 그 정도는")).isEqualTo("흥, 그 정도는");
        assertThat(ReplyStream.partial("흥. {\"emotion\":\"경멸\"}")).isEqualTo("흥.");
        assertThat(ReplyStream.partial("배열은 a[0]부터")).isEqualTo("배열은 a[0]부터");
        // 대사 속 낱말 emotion · { 로 시작하는 글은 자르지 않는다
        assertThat(ReplyStream.partial("Emotion은 감정이란 뜻이야")).isEqualTo("Emotion은 감정이란 뜻이야");
        assertThat(ReplyStream.partial("{1, 2}부터 봐")).isEqualTo("{1, 2}부터 봐");
        assertThat(ReplyStream.partial("{")).isNull();
        assertThat(ReplyStream.partial("```json\n{\"reply\":\"응")).isEqualTo("응");
        assertThat(ReplyStream.partial("```json\n")).isNull();
    }
}
