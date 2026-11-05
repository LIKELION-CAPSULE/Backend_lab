package com.capsule.dialogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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
import com.capsule.dialogue.config.CharacterContentSeeder;
import com.capsule.dialogue.config.DialogueProperties;
import com.capsule.dialogue.dto.LlmPurpose;
import com.capsule.dialogue.service.ChatReplier;
import com.capsule.relation.api.RelationCommands;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.api.RelationTypes.AffinityChange;
import com.capsule.relation.api.RelationTypes.ChatScore;
import com.capsule.relation.api.RelationTypes.RelationView;
import com.capsule.relation.api.RelationTypes.SignalType;
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

    @MockitoBean // 캐릭터 설정(말포이)을 불러오지 않는다: 이 테스트는 설정 없는 캐릭터(공통 고정 대사 · SIMPLE) 기준
    CharacterContentSeeder seeder;

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

    @Autowired
    DialogueProperties props;

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
        when(relationQuery.view(userId, CHARACTER_ID)).thenReturn(view(1, 80));
    }

    /** 관계 단계(stage)와 호감도만 의미 있는 조회 결과 */
    private static RelationView view(int stage, int points) {
        return new RelationView(CHARACTER_ID, 1, "단계" + stage, points, 0, null, stage, "단계" + stage, 0, 3600);
    }

    private static AffinityChange change(int delta, int points) {
        return new AffinityChange(delta, points, 1, false, 1, "단계1");
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
        when(relationQuery.view(userId, CHARACTER_ID)).thenReturn(view(3, 80));

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
        when(relationCommands.applyChatScore(eq(userId), eq(CHARACTER_ID), eq(sessionId), eq(requestId), any()))
                .thenReturn(change(1, 81));

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

    // --- 28 메시지 스트리밍 ---

    @Test
    void streamMessageSendsReplyThenDone() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(relationCommands.applyChatScore(eq(userId), eq(CHARACTER_ID), eq(sessionId), eq(requestId), any()))
                .thenReturn(change(1, 81));

        String body = streamed(requestId, "이거 너무 어려워", MediaType.TEXT_EVENT_STREAM);
        // 대사가 먼저 reply로, 끝에 20번 응답 전체가 done으로 (Fake 공급자는 원문을 한 번에 넘긴다)
        assertThat(body).contains("event:reply\ndata:{\"content\":\"「이거 너무 어려워」… 그렇구나. 한 문제만 더 해보자.\"}");
        assertThat(body.indexOf("event:reply")).isLessThan(body.indexOf("event:done"));
        assertThat(body)
                .contains("event:done\ndata:{\"requestId\":\"" + requestId + "\"")
                .contains("\"affinity\":{\"delta\":1")
                .contains("\"fallback\":false")
                .doesNotContain("event:error");
        assertThat(rows(requestId)).isEqualTo(2);

        // 재전송은 저장된 답장을 done 한 번으로 (20과 같은 규칙: 감정 null)
        String again = streamed(requestId, "이거 너무 어려워", MediaType.TEXT_EVENT_STREAM);
        assertThat(again).doesNotContain("event:reply").contains("event:done").contains("\"emotion\":null");
        assertThat(rows(requestId)).isEqualTo(2);
    }

    @Test
    void streamWorksBehindAProxyThatOnlyAcceptsJson() throws Exception {
        // 프론트 프록시는 지금 accept: application/json을 붙인다. 그래도 스트림이 와야 프록시를 안 고쳐도 된다
        String body = streamed(UUID.randomUUID(), "안녕", MediaType.APPLICATION_JSON);
        assertThat(body).contains("event:reply").contains("event:done");
    }

    @Test
    void streamErrorsBeforeOpeningAreJson() throws Exception {
        mvc.perform(post("/api/sessions/{sessionId}/messages/stream", sessionId)
                        .with(jwt().jwt(j -> j.subject(String.valueOf(userId))))
                        // 명세 28: Accept는 둘 다 (event-stream만 보내면 에러 JSON을 쓸 수 없어 실패한다)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"characterId\":999,\"content\":\"안녕\","
                                + "\"inputType\":\"TEXT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHARACTER_NOT_IN_SESSION"));
    }

    @Test
    void streamWithoutAFirstWordInTimeFallsBack() throws Exception {
        doAnswer(invocation -> {
                    Thread.sleep(6_000);
                    return invocation.callRealMethod();
                })
                .when(llm)
                .complete(any(), any(), any(), any(), any());
        UUID requestId = UUID.randomUUID();
        when(relationCommands.applyChatScore(userId, CHARACTER_ID, sessionId, requestId, ChatScore.none()))
                .thenReturn(change(0, 80));

        String body = streamed(requestId, "이거 어려워", MediaType.TEXT_EVENT_STREAM);
        // 첫 대사가 5초 안에 안 왔으니 reply 없이 대체 대사 done 한 번 (늦게 온 글은 보내지 않는다)
        assertThat(body).doesNotContain("event:reply");
        assertThat(body).contains("\"content\":\"미안, 잠깐 딴생각했어. 다시 말해줄래?\"").contains("\"fallback\":true");
        verify(relationCommands).applyChatScore(userId, CHARACTER_ID, sessionId, requestId, ChatScore.none());
    }

    @Test
    void streamKeepsWaitingOnceTheFirstWordIsOut() throws Exception {
        // 첫 대사는 바로 나왔는데 끝나는 데 6초: 이미 보인 글을 대체 대사로 바꾸지 않고 끝까지 기다린다
        doAnswer(invocation -> {
                    java.util.function.Consumer<String> onText = invocation.getArgument(4);
                    if (invocation.getArgument(0) != LlmPurpose.CHAT || onText == null)
                        return invocation.callRealMethod();
                    onText.accept("{\"reply\":\"잠깐만");
                    Object result = invocation.callRealMethod(); // 게이트웨이는 시작할 때만 마감을 본다
                    Thread.sleep(6_000); // 스트림이 늦게 끝남
                    return result;
                })
                .when(llm)
                .complete(any(), any(), any(), any(), any());
        UUID requestId = UUID.randomUUID();
        when(relationCommands.applyChatScore(eq(userId), eq(CHARACTER_ID), eq(sessionId), eq(requestId), any()))
                .thenReturn(change(0, 80));

        String body = streamed(requestId, "이거 어려워", MediaType.TEXT_EVENT_STREAM);
        assertThat(body).contains("event:reply\ndata:{\"content\":\"잠깐만\"}");
        assertThat(body)
                .contains("\"content\":\"「이거 어려워」… 그렇구나. 한 문제만 더 해보자.\"")
                .contains("\"fallback\":false");
    }

    /** 28을 불러 스트림이 끝날 때까지 받은 본문 */
    private String streamed(UUID requestId, String content, MediaType accept) throws Exception {
        MvcResult started = mvc.perform(post("/api/sessions/{sessionId}/messages/stream", sessionId)
                        .with(jwt().jwt(j -> j.subject(String.valueOf(userId))))
                        .accept(accept)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"" + requestId + "\",\"characterId\":" + CHARACTER_ID
                                + ",\"content\":\"" + content + "\",\"inputType\":\"TEXT\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mvc.perform(asyncDispatch(started)).andExpect(status().isOk());
        return started.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 이 말을 보내고 relation에 넘긴 점수 */
    private ChatScore scoreOf(String content) throws Exception {
        UUID id = UUID.randomUUID();
        send(id, content).andExpect(status().isOk());
        org.mockito.ArgumentCaptor<ChatScore> score = org.mockito.ArgumentCaptor.forClass(ChatScore.class);
        verify(relationCommands).applyChatScore(eq(userId), eq(CHARACTER_ID), eq(sessionId), eq(id), score.capture());
        return score.getValue();
    }

    @Test
    void chatIsScoredByPatternAndSessionRules() throws Exception {
        // A안: 말 패턴으로 종류, 점수표(application.yml chat-scores)로 점수. 결정 모델은 부르지 않는다
        assertThat(scoreOf("오늘 날씨 좋다")).isEqualTo(new ChatScore(SignalType.NEUTRAL, 0, 0));
        assertThat(scoreOf("고마워 진짜")).isEqualTo(new ChatScore(SignalType.THANKS, 1, 0));
        assertThat(scoreOf("고맙다 정말")).isEqualTo(new ChatScore(SignalType.THANKS, 1, 0));
        assertThat(scoreOf("감사합니다요")).isEqualTo(new ChatScore(SignalType.THANKS, 0, 0)); // 10턴 안 세 번째
        assertThat(scoreOf("요즘 너무 힘들어")).isEqualTo(new ChatScore(SignalType.CONFIDE, 2, 0));
        assertThat(scoreOf("진짜 불안해 죽겠어")).isEqualTo(new ChatScore(SignalType.CONFIDE, 0, 0)); // 세션당 1번
        assertThat(scoreOf("아 꺼져 좀")).isEqualTo(new ChatScore(SignalType.RUDE, -2, 0));
        assertThat(scoreOf("닥쳐 진짜")).isEqualTo(new ChatScore(SignalType.RUDE, -3, 0)); // 같은 세션 반복
        assertThat(jdbc.queryForObject(
                        "SELECT signal FROM chat_messages WHERE session_id = ? AND sender = 'USER' ORDER BY id LIMIT 1",
                        String.class,
                        sessionId))
                .isEqualTo("NEUTRAL");
    }

    @Test
    void severeWordsAreConfirmedByTheDecider() throws Exception {
        // 패턴이 혐오 · 성적이라고 하면 결정 모델이 확인한다. Fake는 첫 보기(HATE_OR_SEXUAL, 0.9)를 고른다
        assertThat(scoreOf("섹스 얘기 하자")).isEqualTo(new ChatScore(SignalType.HATE_OR_SEXUAL, -5, 30));

        // 확인이 낮은 확신이면 RUDE로 (비율 감점 오탐 방지)
        doAnswer(inv -> {
                    Map<?, ?> questions = inv.getArgument(1);
                    return questions.containsKey(ChatReplier.QUESTION)
                            ? Map.of(ChatReplier.QUESTION, new Decider.Decision("HATE_OR_SEXUAL", 0.5))
                            : inv.callRealMethod();
                })
                .when(decider)
                .decide(anyString(), any(), any());
        assertThat(scoreOf("야동 같은 소리")).isEqualTo(new ChatScore(SignalType.RUDE, -2, 0));

        // 확인 호출이 실패해도 답장은 나가고 RUDE (같은 세션 두 번째라 −3)
        doThrow(new IllegalStateException("결정 실패")).when(decider).decide(anyString(), any(), any());
        UUID failed = UUID.randomUUID();
        send(failed, "섹드립 ㄱ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallback").value(false));
        verify(relationCommands)
                .applyChatScore(userId, CHARACTER_ID, sessionId, failed, new ChatScore(SignalType.RUDE, -3, 0));
    }

    @Test
    void factsAreKeptUpdatedAndCappedAt20() throws Exception {
        // 결정 모델이 '새로 저장'을 고르면 LLM이 한 줄로 쓴다
        doAnswer(inv -> {
                    Map<?, ?> questions = inv.getArgument(1);
                    return questions.containsKey("memory")
                            ? Map.of("memory", new Decider.Decision("ADD", 0.9))
                            : inv.callRealMethod();
                })
                .when(decider)
                .decide(anyString(), any(), any());
        send(UUID.randomUUID(), "나 이번 달에 토익 800 목표야").andExpect(status().isOk());
        assertThat(jdbc.queryForList("SELECT content FROM character_memories WHERE user_id = ?", String.class, userId))
                .singleElement()
                .asString()
                .startsWith("(가짜 기억)");

        // 20개가 차 있으면 하나 버리고 넣는다 (Fake는 버릴 것으로 첫 보기 = 가장 오래된 것)
        for (int i = 0; i < 19; i++) {
            jdbc.update(
                    "INSERT INTO character_memories (user_id, character_id, content) VALUES (?, ?, ?)",
                    userId,
                    CHARACTER_ID,
                    "사실 " + i);
        }
        send(UUID.randomUUID(), "나 밤에 공부가 잘 돼").andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM character_memories WHERE user_id = ?", Integer.class, userId))
                .isEqualTo(20);

        // 전화번호 같은 개인정보는 결정 모델에 묻지도 않는다
        int before = jdbc.queryForObject("SELECT count(*) FROM llm_usages WHERE purpose = 'MEMORY'", Integer.class);
        send(UUID.randomUUID(), "내 번호 010-1234-5678 이야").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM llm_usages WHERE purpose = 'MEMORY'", Integer.class))
                .isEqualTo(before);
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
        when(relationCommands.applyChatScore(eq(userId), eq(CHARACTER_ID), eq(sessionId), eq(requestId), any()))
                .thenReturn(change(1, 81));
        send(requestId, "졸려").andExpect(status().isOk());

        send(requestId, "졸려")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("「졸려」… 그렇구나. 한 문제만 더 해보자."))
                .andExpect(jsonPath("$.affinity.delta").value(0))
                .andExpect(jsonPath("$.affinity.points").value(80));
        verify(llm, times(1)).complete(eq(LlmPurpose.CHAT), any(), any(), any(), any());
        verify(relationCommands, times(1)).applyChatScore(anyLong(), anyLong(), anyLong(), any(), any());
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
    void messageOverTheLimitInAMinuteIsRejected() throws Exception {
        for (int i = 0; i < props.messageLimit(); i++) {
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
        for (int i = 0; i < props.messageLimit() - 1; i++) {
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

        // 한도의 마지막 요청이 LLM을 기다리는 동안 같은 requestId로 재전송 → 한도에 세지 않고 같은 답장
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
        when(relationCommands.applyChatScore(userId, CHARACTER_ID, sessionId, requestId, ChatScore.none()))
                .thenReturn(change(0, 80));

        send(requestId, "이거 어려워")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("미안, 잠깐 딴생각했어. 다시 말해줄래?"))
                .andExpect(jsonPath("$.emotion").value(nullValue()))
                .andExpect(jsonPath("$.fallback").value(true))
                .andExpect(jsonPath("$.affinity.delta").value(0))
                .andExpect(jsonPath("$.affinity.points").value(80));
        // 대체 대사면 0점으로만 부른다 (호감도 변화 없음, relation 잠금 순서만 지킴)
        verify(relationCommands).applyChatScore(userId, CHARACTER_ID, sessionId, requestId, ChatScore.none());
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
    void closeWritesARecapCardDeletesMessagesAndReturnsClosingLine() throws Exception {
        send(UUID.randomUUID(), "파이팅").andExpect(status().isOk());
        long conversationId = jdbc.queryForObject("SELECT id FROM conversations WHERE user_id = ?", Long.class, userId);
        // 앞선 종료에서 카드 쓰기가 실패해 남은 다른 세션 메시지도 이번 카드에 같이 들어간다
        long earlier = jdbc.queryForObject(
                "INSERT INTO study_sessions (user_id, room_id, background_id, ended_at, end_type)"
                        + " VALUES (?, 1, 1, now(), 'AUTO_CLOSED') RETURNING id",
                Long.class,
                userId);
        jdbc.update(
                "INSERT INTO chat_messages (conversation_id, session_id, request_id, sender, content)"
                        + " VALUES (?, ?, ?, 'USER', '지난번 남은 말')",
                conversationId,
                earlier,
                UUID.randomUUID());
        // 카드는 최근 recap-max(2)장만 남는다
        for (long s = 1; s <= 3; s++) {
            jdbc.update(
                    "INSERT INTO conversation_recaps (conversation_id, session_id, content) VALUES (?, ?, ?)",
                    conversationId,
                    -s,
                    "옛 카드 " + s);
        }

        SessionClosing closing = new SessionClosing(sessionId, userId, CHARACTER_ID, "채린", "NORMAL", 3300, null);
        SessionClosingHandler.Draft draft = closingHandler.draft(closing); // 공부 종료처럼 트랜잭션 밖에서 먼저
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM conversation_recaps WHERE conversation_id = ?",
                        Integer.class,
                        conversationId))
                .isEqualTo(3); // 초안은 DB에 쓰지 않는다
        // 초안을 만든 뒤에 들어온 말은 카드에 안 들어갔으므로 지우지 않는다 (다음 종료 카드로)
        jdbc.update(
                "INSERT INTO chat_messages (conversation_id, session_id, request_id, sender, content)"
                        + " VALUES (?, ?, ?, 'USER', '초안 뒤에 온 말')",
                conversationId,
                sessionId,
                UUID.randomUUID());
        String closingLine = closingHandler.close(closing, draft);

        assertThat(closingLine).isEqualTo("(가짜 완료 대사) 오늘 공부 수고했어."); // LLM이 만든 것 (대체 문구가 아님)
        List<String> cards = jdbc.queryForList(
                "SELECT content FROM conversation_recaps WHERE conversation_id = ? ORDER BY id DESC",
                String.class,
                conversationId);
        assertThat(cards).hasSize(2).containsExactly(cards.getFirst(), "옛 카드 3");
        // 머리말(날짜 · 공부 시간)은 코드가 붙인다. Fake 요약은 입력 앞부분을 돌려준다
        assertThat(cards.getFirst()).matches("\\d{2}/\\d{2} 55분, \\(가짜 요약\\) <아는 사실>.*");
        assertThat(jdbc.queryForList(
                        "SELECT content FROM chat_messages WHERE conversation_id = ?", String.class, conversationId))
                .containsExactly("초안 뒤에 온 말");
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
