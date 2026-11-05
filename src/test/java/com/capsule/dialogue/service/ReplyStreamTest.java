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
        // JSON 없이 글만 쓰는 답은 그 글 (뒤에 붙는 {"emotion"} 앞까지)
        assertThat(ReplyStream.partial("흥, 그 정도는")).isEqualTo("흥, 그 정도는");
        assertThat(ReplyStream.partial("흥. {\"emotion\":\"경멸\"}")).isEqualTo("흥.");
        assertThat(ReplyStream.partial("기록 갱신. emotion=웃")).isEqualTo("기록 갱신.");
        assertThat(ReplyStream.partial("기록 갱신. (emotion")).isEqualTo("기록 갱신.");
        assertThat(ReplyStream.partial("```json\n{\"reply\":\"응")).isEqualTo("응");
        assertThat(ReplyStream.partial("```json\n")).isNull();
    }
}
