package com.capsule.dialogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
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
import com.capsule.dialogue.client.Decider;
import com.capsule.dialogue.client.LlmGateway;
import com.capsule.dialogue.dto.LlmPurpose;
import com.capsule.dialogue.service.ChatReplier;
import com.capsule.relation.api.RelationCommands;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.api.RelationTypes.AffinityChange;
import com.capsule.relation.api.RelationTypes.ChatSignal;
import com.capsule.relation.api.RelationTypes.RelationView;
import com.capsule.session.api.SessionClosingHandler;
import com.capsule.session.api.SessionQuery;
import com.capsule.session.api.SessionTypes.SessionClosing;
import com.capsule.session.api.SessionTypes.SessionEventInfo;
import com.capsule.session.api.SessionTypes.SessionInfo;
import com.capsule.user.api.MemoryQuery;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

/**
 * 이벤트 반응(19)·메시지(20)·종료 마무리. 다른 모듈 api는 전부 목, LLM은 Fake 공급자를 쓰는 게이트웨이의 스파이.
 * RETURN만 LLM 반응으로 켜서 고정 대사와 LLM 대사 두 길을 다 본다.
 */
@SpringBootTest(properties = "capsule.dialogue.reaction-llm-triggers=RETURN")
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
    LlmGateway llm;

    @MockitoSpyBean
    Decider decider;

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
                .andExpect(jsonPath("$.characters[0].content").value(in(List.of("왔구나. 오늘도 같이 해볼까?", "안녕. 오늘도 잘 부탁해."))))
                .andExpect(jsonPath("$.characters[0].emotion").value("HAPPY"))
                .andExpect(jsonPath("$.characters[0].motion.group").value("TapBody"))
                .andExpect(jsonPath("$.characters[0].expression.file").value("exp/smile.exp3.json"))
                .andExpect(jsonPath("$.characters[0].affinity").doesNotExist())
                .andExpect(jsonPath("$.characters[0].fallback").value(false))
                .andExpect(jsonPath("$.characters[0].audioBase64").value(nullValue()));
        verify(reactionQuery).pick(CHARACTER_ID, "HAPPY", null); // 명세 19: trigger가 아니라 감정으로 고름
        verifyNoInteractions(relationCommands); // 호감도는 18이 맡는다 (여기선 단계만 읽음)
    }

    @Test
    void awayIntensityGoesToLineNotMotion() throws Exception {
        event(11, "AWAY", false, false);

        react(11, "{\"requestId\":\"" + UUID.randomUUID() + "\",\"intensity\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.characters[0].content").value(in(List.of("아직이야? 기다리고 있을게.", "언제 와?"))))
                .andExpect(jsonPath("$.characters[0].emotion").value("SAD"))
                .andExpect(jsonPath("$.characters[0].motion").value(nullValue()));
        verify(reactionQuery).pick(CHARACTER_ID, "SAD", null);
    }

    @Test
    void awayIntensityOutOfRangeIsRejected() throws Exception {
        event(16, "AWAY", false, false);
        for (int intensity : new int[] {0, 4}) {
            react(16, "{\"requestId\":\"" + UUID.randomUUID() + "\",\"intensity\":" + intensity + "}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
    }

    @Test
    void motionEventsGetTheirOwnLines() throws Exception {
        String[][] cases = {
            {"DROWSY", "졸려? 기지개 한 번 켜고 하자.", "눈이 감기는 것 같은데… 괜찮아?", "NEUTRAL"},
            {"YAWN", "하품했지? 물 한 모금 마시고 와.", "피곤하구나. 스트레칭 한 번 하자.", "NEUTRAL"},
            {"WAVE", "나도 안녕! 계속 해 보자.", "응, 나 여기 있어.", "HAPPY"},
            {"HEAD_TURN", "어디 봐? 여기 집중!", "뭐 있어? 다시 여기 보자.", "NEUTRAL"}
        };
        long eventId = 20;
        for (String[] c : cases) {
            event(eventId, c[0], false, false);
            react(eventId++, "{\"requestId\":\"" + UUID.randomUUID() + "\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.characters[0].content").value(in(List.of(c[1], c[2]))))
                    .andExpect(jsonPath("$.characters[0].emotion").value(c[3]))
                    .andExpect(jsonPath("$.characters[0].fallback").value(false));
        }
    }

    @Test
    void fixedLineFollowsRelationLevel() throws Exception {
        event(15, "AWAY", false, false);
        when(relationQuery.view(userId, CHARACTER_ID))
                .thenReturn(new RelationView(CHARACTER_ID, 3, "Lv.3", 320, 0, null));

        // Lv.3: AWAY의 3 이상 칸. 강도 2도 AWAY_2의 3 이상 칸
        react(15, "{\"requestId\":\"" + UUID.randomUUID() + "\"}")
                .andExpect(jsonPath("$.characters[0].content")
                        .value(in(List.of("또 도망가는 거야~?", "빨리 와. 혼자 있으면 심심하단 말이야."))));
        react(15, "{\"requestId\":\"" + UUID.randomUUID() + "\",\"intensity\":2}")
                .andExpect(
                        jsonPath("$.characters[0].content").value(in(List.of("1분이나 지났어. 나 삐질 거야?", "어디서 뭐 해~ 빨리 와."))));
    }

    @Test
    void focusAfterBreakIsBreakEndAndIgnoresIntensity() throws Exception {
        event(12, "FOCUS", false, true);

        react(12, "{\"requestId\":\"" + UUID.randomUUID() + "\",\"intensity\":3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.characters[0].content").value(in(List.of("다시 시작해볼까?", "좋아, 이어서 하자."))));
        verify(reactionQuery).pick(CHARACTER_ID, "NEUTRAL", null);
    }

    @Test
    void llmReactionIsGeneratedAndLogged() throws Exception {
        event(14, "RETURN", false, false);

        react(14, "{\"requestId\":\"" + UUID.randomUUID() + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.characters[0].content").value(org.hamcrest.Matchers.startsWith("(가짜 반응)")))
                .andExpect(jsonPath("$.characters[0].emotion").value("NEUTRAL"))
                .andExpect(jsonPath("$.characters[0].fallback").value(false));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM llm_usages WHERE session_id = ? AND purpose = 'REACTION'"
                                + " AND source = 'APP' AND status = 'OK' AND cardinality(prompt_version_ids) = 2",
                        Integer.class,
                        sessionId))
                .isEqualTo(1);
    }

    @Test
    void slowLlmReactionFallsBackToFixedLineWithoutEmotion() throws Exception {
        event(13, "RETURN", false, false);
        doAnswer(invocation -> {
                    Thread.sleep(6_000);
                    return invocation.callRealMethod();
                })
                .when(llm)
                .complete(any(), any(), any(), any(), any());

        react(13, "{\"requestId\":\"" + UUID.randomUUID() + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.characters[0].content").value(in(List.of("왔네. 이어서 하자.", "돌아왔구나. 다시 해볼까?"))))
                .andExpect(jsonPath("$.characters[0].emotion").value(nullValue()))
                .andExpect(jsonPath("$.characters[0].motion").value(nullValue()))
                .andExpect(jsonPath("$.characters[0].fallback").value(true));
        verify(reactionQuery, never()).pick(anyLong(), anyString(), any()); // 감정이 없으면 동작 없음
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
        // 사용자 말 신호는 결정 모델이 고른다 (여기선 PRAISE로), 점수는 relation이 정한다
        doReturn(Map.of(ChatReplier.QUESTION, new Decider.Decision("PRAISE", 0.9)))
                .when(decider)
                .decide(anyString(), any(), any());
        when(relationCommands.applyChatSignal(userId, CHARACTER_ID, sessionId, requestId, ChatSignal.PRAISE))
                .thenReturn(new AffinityChange(1, 81, 1, false));

        send(requestId, "이거 너무 어려워")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.characterId").value(CHARACTER_ID))
                .andExpect(jsonPath("$.content").value("「이거 너무 어려워」… 그렇구나. 한 문제만 더 해보자."))
                .andExpect(jsonPath("$.emotion").value("NEUTRAL"))
                .andExpect(jsonPath("$.motion").value(nullValue()))
                .andExpect(jsonPath("$.expression").value(nullValue()))
                .andExpect(jsonPath("$.affinity.delta").value(1))
                .andExpect(jsonPath("$.affinity.points").value(81))
                .andExpect(jsonPath("$.fallback").value(false))
                .andExpect(jsonPath("$.audioBase64").value(nullValue()));
        assertThat(rows(requestId)).isEqualTo(2);
        // 호출 기록: 서비스 호출은 원문을 남기지 않는다(log-content=false), 쓴 프롬프트 버전은 남긴다
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM llm_usages WHERE session_id = ? AND purpose = 'CHAT' AND provider = 'fake'"
                                + " AND request IS NULL AND cardinality(prompt_version_ids) = 2",
                        Integer.class,
                        sessionId))
                .isEqualTo(1);
    }

    @Test
    void signalIsReadInParallelAndUnsureMeansNeutral() throws Exception {
        // Fake 결정 모델은 첫 보기(NEUTRAL)를 고르고, 그 호출도 기록된다
        UUID first = UUID.randomUUID();
        send(first, "오늘 날씨 좋다").andExpect(status().isOk());
        verify(relationCommands).applyChatSignal(userId, CHARACTER_ID, sessionId, first, ChatSignal.NEUTRAL);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM llm_usages WHERE session_id = ? AND purpose = 'DECIDE' AND status = 'OK'",
                        Integer.class,
                        sessionId))
                .isEqualTo(1);

        // 확신이 기준(0.6)보다 낮으면 무례로 읽혀도 0점(NEUTRAL)
        doReturn(Map.of(ChatReplier.QUESTION, new Decider.Decision("RUDE", 0.4)))
                .when(decider)
                .decide(anyString(), any(), any());
        UUID unsure = UUID.randomUUID();
        send(unsure, "아 진짜").andExpect(status().isOk());
        verify(relationCommands).applyChatSignal(userId, CHARACTER_ID, sessionId, unsure, ChatSignal.NEUTRAL);

        // 결정 호출이 실패해도 답장은 나간다 (NEUTRAL)
        doThrow(new IllegalStateException("결정 실패")).when(decider).decide(anyString(), any(), any());
        UUID failed = UUID.randomUUID();
        send(failed, "파이팅")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(false));
        verify(relationCommands).applyChatSignal(userId, CHARACTER_ID, sessionId, failed, ChatSignal.NEUTRAL);
    }

    @Test
    void replyEmotionPicksMotionAndExpression() throws Exception {
        // Fake 답장의 감정은 NEUTRAL
        when(reactionQuery.pick(CHARACTER_ID, "NEUTRAL", null))
                .thenReturn(new Reaction(
                        new MotionRef("Idle", "motions/nod.motion3.json"),
                        new ExpressionRef("calm", "exp/calm.exp3.json")));

        send(UUID.randomUUID(), "이거 너무 어려워")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emotion").value("NEUTRAL"))
                .andExpect(jsonPath("$.motion.file").value("motions/nod.motion3.json"))
                .andExpect(jsonPath("$.expression.name").value("calm"));
    }

    @Test
    void characterMustBeTheSessionCharacter() throws Exception {
        mvc.perform(post("/api/sessions/{sessionId}/messages", sessionId)
                        .with(jwt().jwt(j -> j.subject(String.valueOf(userId))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"" + UUID.randomUUID()
                                + "\",\"characterId\":2,\"content\":\"안녕\",\"inputType\":\"TEXT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHARACTER_NOT_IN_SESSION"));
        verify(llm, never()).complete(any(), any(), any(), any(), any());
    }

    @Test
    void resendWithSameRequestIdIsHandledOnce() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(relationCommands.applyChatSignal(userId, CHARACTER_ID, sessionId, requestId, ChatSignal.NEUTRAL))
                .thenReturn(new AffinityChange(1, 81, 1, false));
        send(requestId, "졸려").andExpect(status().isOk());

        send(requestId, "졸려")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("「졸려」… 그렇구나. 한 문제만 더 해보자."))
                .andExpect(jsonPath("$.affinity.delta").value(0))
                .andExpect(jsonPath("$.affinity.points").value(80));
        verify(llm, times(1)).complete(eq(LlmPurpose.CHAT), any(), any(), any(), any());
        verify(relationCommands, times(1)).applyChatSignal(anyLong(), anyLong(), anyLong(), any(), any());
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
    void eleventhMessageInAMinuteIsRejected() throws Exception {
        for (int i = 0; i < 10; i++) {
            send(UUID.randomUUID(), "파이팅").andExpect(status().isOk());
        }
        UUID rejected = UUID.randomUUID();
        send(rejected, "파이팅")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        assertThat(rows(rejected)).isZero(); // 한도 검사 전에 저장한 사용자 말은 지운다
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
                .complete(any(), any(), any(), any(), any());

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
    void slowLlmFallsBackWithoutAffinityChange() throws Exception {
        doAnswer(invocation -> {
                    Thread.sleep(6_000);
                    return invocation.callRealMethod();
                })
                .when(llm)
                .complete(any(), any(), any(), any(), any());
        UUID requestId = UUID.randomUUID();
        when(relationCommands.applyChatSignal(userId, CHARACTER_ID, sessionId, requestId, ChatSignal.NEUTRAL))
                .thenReturn(new AffinityChange(0, 80, 1, false));

        send(requestId, "이거 어려워")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("미안, 잠깐 딴생각했어. 다시 말해줄래?"))
                .andExpect(jsonPath("$.emotion").value(nullValue()))
                .andExpect(jsonPath("$.fallback").value(true))
                .andExpect(jsonPath("$.affinity.delta").value(0))
                .andExpect(jsonPath("$.affinity.points").value(80));
        // 대체 대사면 NEUTRAL로만 부른다 (호감도 변화 없음, relation 잠금 순서만 지킴)
        verify(relationCommands, never())
                .applyChatSignal(
                        anyLong(),
                        anyLong(),
                        anyLong(),
                        any(),
                        org.mockito.ArgumentMatchers.argThat(s -> s != ChatSignal.NEUTRAL));
    }

    @Test
    void resendWhileFirstIsStillAnsweringWaitsForThatAnswer() throws Exception {
        UUID requestId = UUID.randomUUID();
        send(UUID.randomUUID(), "대화방 만들기").andExpect(status().isOk());
        long conversationId = jdbc.queryForObject("SELECT id FROM conversations WHERE user_id = ?", Long.class, userId);
        // 먼저 온 요청이 사용자 말을 저장하고 LLM을 기다리는 중인 상태를 만든다. 1초 뒤 그 요청이 답장을 저장한다
        jdbc.update(
                "INSERT INTO chat_messages (conversation_id, session_id, request_id, sender, content) VALUES (?, ?, ?, 'USER', '처리 중')",
                conversationId,
                sessionId,
                requestId);
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            jdbc.update(
                    "INSERT INTO chat_messages (conversation_id, session_id, request_id, sender, content) VALUES (?, ?, ?, 'CHARACTER', '먼저 온 요청의 답')",
                    conversationId,
                    sessionId,
                    requestId);
        });

        send(requestId, "처리 중")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("먼저 온 요청의 답"))
                .andExpect(jsonPath("$.affinity.delta").value(0));
        verify(llm, times(1)).complete(eq(LlmPurpose.CHAT), any(), any(), any(), any()); // 대화방 만들기 한 번뿐
    }

    @Test
    void staleQuestionWithoutAnswerIsTakenOverAtOnce() throws Exception {
        UUID requestId = UUID.randomUUID();
        send(UUID.randomUUID(), "대화방 만들기").andExpect(status().isOk());
        long conversationId = jdbc.queryForObject("SELECT id FROM conversations WHERE user_id = ?", Long.class, userId);
        // 1분 전에 저장되고 답이 없는 사용자 말 = 먼저 온 요청이 중간에 죽음
        jdbc.update(
                "INSERT INTO chat_messages (conversation_id, session_id, request_id, sender, content, created_at)"
                        + " VALUES (?, ?, ?, 'USER', '졸려', now() - interval '1 minute')",
                conversationId,
                sessionId,
                requestId);

        long started = System.nanoTime();
        send(requestId, "졸려")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("「졸려」… 그렇구나. 한 문제만 더 해보자."));
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(3_000); // 8초를 기다리지 않는다
        assertThat(rows(requestId)).isEqualTo(2);
    }

    @Test
    void sessionEndedWhileAnsweringLeavesNothingBehind() throws Exception {
        UUID requestId = UUID.randomUUID();
        // 요청을 받을 때는 진행 중, 답장을 저장하기 직전에는 끝남 (그사이 공부 종료가 커밋됨)
        when(sessionQuery.find(sessionId))
                .thenReturn(Optional.of(new SessionInfo(sessionId, userId, 1, CHARACTER_ID, Instant.now(), null)))
                .thenReturn(
                        Optional.of(new SessionInfo(sessionId, userId, 1, CHARACTER_ID, Instant.now(), Instant.now())));

        send(requestId, "다 했다")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_ENDED"));
        assertThat(rows(requestId)).isZero(); // 답장도, 답을 못 받은 사용자 말도 남지 않는다
    }

    // --- 22 종료 때 마무리 ---

    @Test
    void closeMergesSummaryDeletesMessagesAndReturnsClosingLine() throws Exception {
        send(UUID.randomUUID(), "파이팅").andExpect(status().isOk());
        send(UUID.randomUUID(), "졸려").andExpect(status().isOk());
        jdbc.update("UPDATE conversations SET summary = '이전 요약' WHERE user_id = ?", userId);

        String closingLine =
                closingHandler.close(new SessionClosing(sessionId, userId, CHARACTER_ID, "채린", "NORMAL", 3300));

        assertThat(closingLine).isEqualTo("(가짜 완료 대사) 오늘 공부 수고했어."); // LLM이 만든 것 (대체 문구가 아님)
        // Fake 요약은 입력(이전 요약 + 이번 대화) 앞부분을 그대로 돌려준다: 이전 요약이 입력에 들어갔는지 확인
        assertThat(jdbc.queryForObject("SELECT summary FROM conversations WHERE user_id = ?", String.class, userId))
                .startsWith("(가짜 요약) <이전 요약>\n이전 요약");
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
        return mvc.perform(post("/api/sessions/{sessionId}/messages", sessionId)
                .with(jwt().jwt(j -> j.subject(String.valueOf(userId))))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"" + requestId + "\",\"characterId\":" + CHARACTER_ID + ",\"content\":\""
                        + content + "\",\"inputType\":\"QUICK\"}"));
    }

    private int rows(UUID requestId) {
        return jdbc.queryForObject("SELECT count(*) FROM chat_messages WHERE request_id = ?", Integer.class, requestId);
    }
}
