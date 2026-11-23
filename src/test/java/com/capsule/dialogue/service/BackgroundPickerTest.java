package com.capsule.dialogue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.capsule.common.settings.RuntimeSettings;
import com.capsule.content.api.BackgroundQuery;
import com.capsule.content.api.ContentTypes.BackgroundInfo;
import com.capsule.dialogue.client.Decider;
import com.capsule.dialogue.client.Decider.Decision;
import com.capsule.dialogue.dto.LlmContext;
import com.capsule.session.api.ConnectionBackgrounds;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 배경은 대화 흐름에 따라 결정 모델이 "그대로" 또는 메이트 배경 중 하나를 고른다 (ADR 0032) */
class BackgroundPickerTest {

    static final BackgroundInfo CLASSROOM = new BackgroundInfo("classroom", "교실", "https://cdn/c.png", "공부할 때", true);
    static final BackgroundInfo CAFE = new BackgroundInfo("cafe", "카페", "https://cdn/f.png", "같이 카페에 갔을 때", false);

    final Decider decider = mock(Decider.class);
    final BackgroundQuery query = mock(BackgroundQuery.class);
    final ConnectionBackgrounds connection = mock(ConnectionBackgrounds.class);
    final BackgroundPicker picker =
            new BackgroundPicker(decider, query, connection, RuntimeSettings.fixed(YmlDefaults.DIALOGUE));
    final LlmContext ctx = LlmContext.app(1, 1, Instant.EPOCH);

    @BeforeEach
    void setUp() {
        when(query.backgrounds(1L)).thenReturn(List.of(CLASSROOM, CAFE));
        when(query.current(any(Long.class), any())).thenCallRealMethod();
    }

    @Test
    void choicesAreKeepAndOtherBackgroundsWithGuide() {
        when(decider.decide(anyString(), any(), any()))
                .thenReturn(Map.of(BackgroundPicker.QUESTION, new Decision("cafe", 0.9)));

        assertThat(picker.pick(9, 1, "채린", "카페 가서 하자", "좋아, 가자!", ctx)).contains(CAFE);
        // 보기 = 그대로 + 지금 배경을 뺀 나머지 (guide 포함), 상태 = 지금 배경 + 사용자 말 + 대사, 마감 없이
        verify(decider)
                .decide(
                        contains("채린: 좋아, 가자!"),
                        argThat(q -> {
                            Map<String, String> criteria =
                                    q.get(BackgroundPicker.QUESTION).criteria();
                            return List.copyOf(criteria.keySet()).equals(List.of(BackgroundPicker.KEEP, "cafe"))
                                    && criteria.get("cafe").contains("같이 카페에");
                        }),
                        argThat(c -> c.deadline() == null));
    }

    @Test
    void keepFailureOrTooFewBackgroundsMeansNoChange() {
        when(decider.decide(anyString(), any(), any()))
                .thenReturn(Map.of(BackgroundPicker.QUESTION, new Decision(BackgroundPicker.KEEP, 0.9)));
        assertThat(picker.pick(9, 1, "채린", "카페 싫어", "그래?", ctx)).isEmpty();

        when(decider.decide(anyString(), any(), any())).thenThrow(new IllegalStateException("Jev 실패"));
        assertThat(picker.pick(9, 1, "채린", "카페 가자", "가자!", ctx)).isEmpty();

        doReturn(Map.of()).when(decider).decide(anyString(), any(), any()); // 목록 밖 보기는 Decider가 버림
        assertThat(picker.pick(9, 1, "채린", "카페 가자", "가자!", ctx)).isEmpty();

        // 지금 배경(cafe)을 다시 고르는 보기는 없다
        when(connection.current(9)).thenReturn("cafe");
        doReturn(Map.of(BackgroundPicker.QUESTION, new Decision("classroom", 0.9)))
                .when(decider)
                .decide(anyString(), any(), any());
        assertThat(picker.pick(9, 1, "채린", "이제 공부하자", "응, 돌아가자.", ctx)).contains(CLASSROOM);
    }

    @Test
    void mateWithOneBackgroundDoesNotAsk() {
        when(query.backgrounds(2L)).thenReturn(List.of(CLASSROOM));
        assertThat(picker.pick(9, 2, "채린", "카페 가자", "가자!", ctx)).isEmpty();
        assertThat(picker.pick(9, 1, "채린", "카페 가자", " ", ctx)).isEmpty();
        verify(decider, never()).decide(anyString(), any(), any());
    }
}
