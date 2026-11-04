package com.capsule.dialogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.TestcontainersConfiguration;
import com.capsule.content.api.CharacterQuery;
import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.content.api.ContentTypes.ExpressionRef;
import com.capsule.content.api.ContentTypes.MotionRef;
import com.capsule.content.api.ContentTypes.Reaction;
import com.capsule.content.api.ReactionQuery;
import com.capsule.dialogue.client.LlmClient;
import com.capsule.dialogue.dto.LlmReply;
import com.capsule.relation.api.RelationCommands;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.api.RelationTypes.AffinityChange;
import com.capsule.relation.api.RelationTypes.RelationView;
import com.capsule.session.api.SessionClosingHandler;
import com.capsule.session.api.SessionQuery;
import com.capsule.session.api.SessionTypes.SessionClosing;
import com.capsule.session.api.SessionTypes.SessionEventInfo;
import com.capsule.session.api.SessionTypes.SessionInfo;
import com.capsule.user.api.MemoryQuery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** 이벤트 반응(19)·메시지(20)·종료 마무리. 다른 모듈 api는 전부 목, LLM은 Fake를 감싼 스파이 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DialogueTest {

    private static final long CHARACTER_ID = 1; // 시드 채린

    @MockitoBean
    SessionQuery sessionQuery;

    @MockitoBean
    CharacterQuery characterQuery;

    @MockitoBean
    ReactionQuery reactionQuery;

    @MockitoBean
    RelationQuery relationQuery;

    @MockitoBean
    RelationCommands relationCommands;

    @MockitoBean
    MemoryQuery memoryQuery;

    @MockitoSpyBean
    LlmClient llm;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SessionClosingHandler closingHandler;

    long userId;
    long sessionId;

    @BeforeEach
    void setUp() {
        userId = jdbc.queryForObject(
                "INSERT INTO users (nickname, email, password) VALUES ('테스트', ?, 'x') RETURNING id",
                Long.class,
                UUID.randomUUID() + "@dialogue.test");
        sessionId = jdbc.queryForObject(
                "INSERT INTO study_sessions (user_id, room_id, background_id) VALUES (?, 1, 1) RETURNING id",
                Long.class,
                userId);
        when(sessionQuery.find(sessionId))
                .thenReturn(Optional.of(new SessionInfo(sessionId, userId, 1, CHARACTER_ID, Instant.now(), null)));
        when(characterQuery.find(CHARACTER_ID))
                .thenReturn(
                        Optional.of(new CharacterInfo(CHARACTER_ID, 1, "채린", null, List.of("츤데레"), "짝꿍", true, null)));
        when(reactionQuery.pick(anyLong(), anyString(), any())).thenReturn(Reaction.NONE);
        when(relationQuery.view(userId, CHARACTER_ID))
                .thenReturn(new RelationView(CHARACTER_ID, 1, "아는 사이", 80, 0, 100));
    }

    // --- 19 이벤트 반응 ---

    @Test
    void firstFocusIsGreetingAndMotionFollowsEmotion() throws Exception {
        event(10, "FOCUS", true, false);
        when(reactionQuery.pick(CHARACTER_ID, "HAPPY", null))
                .thenReturn(new Reaction(
                        new MotionRef("TapBody", "motions/wave.motion3.json"),
                        new ExpressionRef("웃음", "exp/smile.exp3.json")));
        UUID requestId = UUID.randomUUID();

        react(10, "{\"requestId\":\"" + requestId + "\",\"tts\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.trigger").doesNotExist())
                .andExpect(jsonPath("$.characters.length()").value(1))
                .andExpect(jsonPath("$.characters[0].characterId").value(CHARACTER_ID))
                .andExpect(jsonPath("$.characters[0].content").value("왔구나. 오늘도 같이 해볼까?"))
                .andExpect(jsonPath("$.characters[0].emotion").value("HAPPY"))
                .andExpect(jsonPath("$.characters[0].motion.group").value("TapBody"))
                .andExpect(jsonPath("$.characters[0].expression.file").value("exp/smile.exp3.json"))
                .andExpect(jsonPath("$.characters[0].affinity").doesNotExist())
                .andExpect(jsonPath("$.characters[0].fallback").value(false))
                .andExpect(jsonPath("$.characters[0].audioBase64").value(nullValue()));
        verify(reactionQuery).pick(CHARACTER_ID, "HAPPY", null); // 명세 19: trigger가 아니라 감정으로 고름
        verifyNoInteractions(relationQuery); // 호감도는 18이 맡는다
    }

    @Test
    void awayIntensityGoesToLineNotMotion() throws Exception {
        event(11, "AWAY", false, false);

        react(11, "{\"requestId\":\"" + UUID.randomUUID() + "\",\"intensity\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.characters[0].content").value("잠깐 자리 비운 거야?"))
                .andExpect(jsonPath("$.characters[0].emotion").value("SAD"))
                .andExpect(jsonPath("$.characters[0].motion").value(nullValue()));
        verify(llm).reaction(any(), eq("AWAY"), eq(2));
        verify(reactionQuery).pick(CHARACTER_ID, "SAD", null);
    }

    @Test
    void awayIntensityOutOfRangeIsRejected() throws Exception {
        event(14, "AWAY", false, false);
        for (int intensity : new int[] {0, 4}) {
            react(14, "{\"requestId\":\"" + UUID.randomUUID() + "\",\"intensity\":" + intensity + "}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        verify(llm, never()).reaction(any(), anyString(), any());
    }

    @Test
    void focusAfterBreakIsBreakEndAndIgnoresIntensity() throws Exception {
        event(12, "FOCUS", false, true);

        react(12, "{\"requestId\":\"" + UUID.randomUUID() + "\",\"intensity\":3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.characters[0].content").value("다시 시작해볼까?"));
        verify(llm).reaction(any(), eq("BREAK_END"), isNull());
        verify(reactionQuery).pick(CHARACTER_ID, "NEUTRAL", null);
    }

    @Test
    void motionEventsGetTheirOwnLines() throws Exception {
        String[][] cases = {
            {"DROWSY", "졸려? 기지개 한 번 켜고 하자.", "NEUTRAL"},
            {"YAWN", "하품했지? 물 한 모금 마시고 와.", "NEUTRAL"},
            {"WAVE", "나도 안녕! 계속 해 보자.", "HAPPY"},
            {"HEAD_TURN", "어디 봐? 여기 집중!", "NEUTRAL"}
        };
        long eventId = 20;
        for (String[] c : cases) {
            event(eventId, c[0], false, false);
            react(eventId++, "{\"requestId\":\"" + UUID.randomUUID() + "\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.characters[0].content").value(c[1]))
                    .andExpect(jsonPath("$.characters[0].emotion").value(c[2]))
                    .andExpect(jsonPath("$.characters[0].fallback").value(false));
        }
    }

    @Test
    void slowLlmReactionFallsBackToFixedLineWithoutEmotion() throws Exception {
        event(13, "RETURN", false, false);
        doAnswer(invocation -> {
                    Thread.sleep(6_000);
                    return invocation.callRealMethod();
                })
                .when(llm)
                .reaction(any(), anyString(), any());

        react(13, "{\"requestId\":\"" + UUID.randomUUID() + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.characters[0].content").value("왔네. 이어서 하자."))
                .andExpect(jsonPath("$.characters[0].emotion").value(nullValue()))
                .andExpect(jsonPath("$.characters[0].motion").value(nullValue()))
                .andExpect(jsonPath("$.characters[0].fallback").value(true));
        verify(reactionQuery, never()).pick(anyLong(), anyString(), any());
    }

    @Test
    void reactionErrors() throws Exception {
        String body = "{\"requestId\":\"" + UUID.randomUUID() + "\"}";
        react(99, body)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
        react(99, "{\"requestId\":\"nope\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        when(sessionQuery.find(sessionId))
                .thenReturn(Optional.of(new SessionInfo(sessionId, userId + 1, 1, CHARACTER_ID, Instant.now(), null)));
        react(99, body)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));

        when(sessionQuery.find(sessionId))
                .thenReturn(
                        Optional.of(new SessionInfo(sessionId, userId, 1, CHARACTER_ID, Instant.now(), Instant.now())));
        react(99, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_ENDED"));
    }

    // --- 20 메시지 ---

    @Test
    void sendMessage() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(relationCommands.applyChat(userId, CHARACTER_ID, sessionId, requestId, 1))
                .thenReturn(new AffinityChange(1, 81, 1, false));

        send(requestId, "이거 너무 어려워")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.characterId").value(CHARACTER_ID))
                .andExpect(jsonPath("$.content").value("「이거 너무 어려워」… 그렇구나. 한 문제만 더 해보자."))
                .andExpect(jsonPath("$.emotion").value(nullValue())) // Fake LLM은 감정을 안 정함 (J-07)
                .andExpect(jsonPath("$.motion").value(nullValue()))
                .andExpect(jsonPath("$.expression").value(nullValue()))
                .andExpect(jsonPath("$.affinity.delta").value(1))
                .andExpect(jsonPath("$.affinity.points").value(81))
                .andExpect(jsonPath("$.fallback").value(false))
                .andExpect(jsonPath("$.audioBase64").value(nullValue()));
        assertThat(rows(requestId)).isEqualTo(2);
        verify(reactionQuery, never()).pick(anyLong(), anyString(), any());
    }

    @Test
    void replyEmotionPicksMotionAndExpression() throws Exception {
        doReturn(new LlmReply("흥, 한 문제만 더 풀어봐.", "ANGRY", 1)).when(llm).reply(any());
        when(reactionQuery.pick(CHARACTER_ID, "ANGRY", null))
                .thenReturn(new Reaction(
                        new MotionRef("TapBody", "motions/arms_crossed.motion3.json"),
                        new ExpressionRef("pout", "exp/pout.exp3.json")));

        send(UUID.randomUUID(), "이거 너무 어려워")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emotion").value("ANGRY"))
                .andExpect(jsonPath("$.motion.file").value("motions/arms_crossed.motion3.json"))
                .andExpect(jsonPath("$.expression.name").value("pout"));
    }

    @Test
    void resendWhileFirstIsStillRunningIsNotCountedAgainstRateLimit() throws Exception {
        for (int i = 0; i < 9; i++) {
            send(UUID.randomUUID(), "파이팅").andExpect(status().isOk());
        }
        UUID requestId = UUID.randomUUID();
        CountDownLatch replying = new CountDownLatch(1);
        doAnswer(invocation -> {
                    replying.countDown();
                    Thread.sleep(2_000);
                    return invocation.callRealMethod();
                })
                .when(llm)
                .reply(any());

        // 10번째 요청이 LLM을 기다리는 동안 같은 requestId로 재전송 → 한도(10)에 세지 않고 같은 답장
        CompletableFuture<MvcResult> first = CompletableFuture.supplyAsync(() -> {
            try {
                return send(requestId, "졸려").andReturn();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertThat(replying.await(5, TimeUnit.SECONDS)).isTrue();

        send(requestId, "졸려")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("「졸려」… 그렇구나. 한 문제만 더 해보자."));
        assertThat(first.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
        assertThat(rows(requestId)).isEqualTo(2);
    }

    @Test
    void resendWithSameRequestIdIsHandledOnce() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(relationCommands.applyChat(userId, CHARACTER_ID, sessionId, requestId, 1))
                .thenReturn(new AffinityChange(1, 81, 1, false));
        send(requestId, "졸려").andExpect(status().isOk());

        send(requestId, "졸려")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("「졸려」… 그렇구나. 한 문제만 더 해보자."))
                .andExpect(jsonPath("$.affinity.delta").value(0))
                .andExpect(jsonPath("$.affinity.points").value(80));
        verify(llm, times(1)).reply(any());
        verify(relationCommands, times(1)).applyChat(anyLong(), anyLong(), anyLong(), any(), anyInt());
        assertThat(rows(requestId)).isEqualTo(2);
    }

    @Test
    void invalidMessages() throws Exception {
        send(UUID.randomUUID(), "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_MESSAGE"));
        send(UUID.randomUUID(), "가".repeat(301))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void messageToCharacterOutsideSessionIsRejected() throws Exception {
        UUID requestId = UUID.randomUUID();
        send(requestId, CHARACTER_ID + 1, "파이팅")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHARACTER_NOT_IN_SESSION"));
        verify(llm, never()).reply(any());
        assertThat(rows(requestId)).isZero();
    }

    @Test
    void eleventhMessageInAMinuteIsRejected() throws Exception {
        for (int i = 0; i < 10; i++) {
            send(UUID.randomUUID(), "파이팅").andExpect(status().isOk());
        }
        UUID rejected = UUID.randomUUID();
        send(rejected, "파이팅")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        assertThat(rows(rejected)).isZero(); // 한도 검사 전에 저장한 사용자 메시지는 롤백된다
    }

    @Test
    void slowLlmFallsBackWithoutAffinityChange() throws Exception {
        doAnswer(invocation -> {
                    Thread.sleep(6_000);
                    return invocation.callRealMethod();
                })
                .when(llm)
                .reply(any());

        send(UUID.randomUUID(), "이거 어려워")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(LlmClient.FALLBACK_REPLY))
                .andExpect(jsonPath("$.fallback").value(true))
                .andExpect(jsonPath("$.affinity.delta").value(0))
                .andExpect(jsonPath("$.affinity.points").value(80));
        verify(relationCommands, never()).applyChat(anyLong(), anyLong(), anyLong(), any(), anyInt());
    }

    // --- 22 종료 때 마무리 ---

    @Test
    void closeMergesSummaryDeletesMessagesAndReturnsClosingLine() throws Exception {
        send(UUID.randomUUID(), "파이팅").andExpect(status().isOk());
        send(UUID.randomUUID(), "졸려").andExpect(status().isOk());
        jdbc.update("UPDATE conversations SET summary = '이전 요약' WHERE user_id = ?", userId);

        String closingLine =
                closingHandler.close(new SessionClosing(sessionId, userId, CHARACTER_ID, "채린", "NORMAL", 3300));

        assertThat(closingLine).isEqualTo("채린: 오늘도 같이 공부해서 좋았어. 다음에도 함께하자.");
        assertThat(jdbc.queryForObject("SELECT summary FROM conversations WHERE user_id = ?", String.class, userId))
                .isEqualTo("이전 요약\n[세션 " + sessionId + "] 메시지 4개");
        assertThat(jdbc.queryForObject(
                        "SELECT summary_updated_at IS NOT NULL FROM conversations WHERE user_id = ?",
                        Boolean.class,
                        userId))
                .isTrue();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM chat_messages WHERE session_id = ?", Integer.class, sessionId))
                .isZero();
    }

    private void event(long eventId, String type, boolean firstFocus, boolean afterBreak) {
        when(sessionQuery.findEvent(sessionId, eventId))
                .thenReturn(Optional.of(
                        new SessionEventInfo(eventId, sessionId, type, Instant.now(), firstFocus, afterBreak)));
    }

    private ResultActions react(long eventId, String body) throws Exception {
        return mvc.perform(post("/api/sessions/{sessionId}/events/{eventId}/reaction", sessionId, eventId)
                .with(jwt().jwt(j -> j.subject(String.valueOf(userId))))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions send(UUID requestId, String content) throws Exception {
        return send(requestId, CHARACTER_ID, content);
    }

    private ResultActions send(UUID requestId, long characterId, String content) throws Exception {
        return mvc.perform(post("/api/sessions/{sessionId}/messages", sessionId)
                .with(jwt().jwt(j -> j.subject(String.valueOf(userId))))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"" + requestId + "\",\"characterId\":" + characterId + ",\"content\":\""
                        + content + "\",\"inputType\":\"QUICK\"}"));
    }

    private int rows(UUID requestId) {
        return jdbc.queryForObject("SELECT count(*) FROM chat_messages WHERE request_id = ?", Integer.class, requestId);
    }
}
