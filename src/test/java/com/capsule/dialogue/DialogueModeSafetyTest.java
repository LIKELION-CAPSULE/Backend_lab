package com.capsule.dialogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.TestcontainersConfiguration;
import com.capsule.content.api.CharacterQuery;
import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.content.api.ContentTypes.Reaction;
import com.capsule.content.api.ReactionQuery;
import com.capsule.dialogue.client.Llm;
import com.capsule.dialogue.client.LlmGateway;
import com.capsule.dialogue.config.CharacterContentSeeder;
import com.capsule.dialogue.dto.LlmPurpose;
import com.capsule.dialogue.service.SafetyChecker;
import com.capsule.relation.api.RelationCommands;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.api.RelationTypes.ChatScore;
import com.capsule.relation.api.RelationTypes.RelationView;
import com.capsule.session.api.ConnectionMessageCounter;
import com.capsule.session.api.SessionQuery;
import com.capsule.session.api.SessionTypes.SessionInfo;
import com.capsule.user.api.MemoryQuery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
 * 12/1 베타 대화 모드 · 크레딧 차감 · 안전(api.md 20 · 28), 대화방 기록 칸(32), 답변 평가(40) · 신고(41).
 * 크레딧은 실제 credit 모듈(DB), 다른 모듈 api는 목, LLM은 Fake 공급자, 안전 판정은 목(기본 = 통과)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DialogueModeSafetyTest {

    private static final long CHARACTER_ID = 1; // 시드 채린
    private static final String REFUSAL = "그런 얘기는 하지 않을래. 우리 다른 얘기 하자.";

    @MockitoBean
    SessionQuery sessionQuery;

    @MockitoBean
    ConnectionMessageCounter messageCounter;

    @MockitoBean
    CharacterQuery characterQuery;

    @MockitoBean // 설정 없는 캐릭터(SIMPLE) 기준
    CharacterContentSeeder seeder;

    @MockitoBean
    ReactionQuery reactionQuery;

    @MockitoBean
    RelationQuery relationQuery;

    @MockitoBean
    RelationCommands relationCommands;

    @MockitoBean
    MemoryQuery memoryQuery;

    @MockitoBean // 기본(목)은 빈 값 = 통과. 위반은 테스트마다 정한다
    SafetyChecker safetyChecker;

    @MockitoSpyBean
    LlmGateway llm;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    long userId;
    long sessionId;

    @BeforeEach
    void setUp() {
        userId = newUser();
        sessionId = jdbc.queryForObject(
                "INSERT INTO connections (user_id, character_id) VALUES (?, 1) RETURNING id", Long.class, userId);
        when(sessionQuery.find(sessionId))
                .thenReturn(Optional.of(new SessionInfo(sessionId, userId, 1, CHARACTER_ID, Instant.now(), null)));
        when(messageCounter.countReply(sessionId)).thenReturn(true);
        when(characterQuery.find(CHARACTER_ID))
                .thenReturn(Optional.of(
                        new CharacterInfo(CHARACTER_ID, 1, "채린", null, List.of("츤데레"), "짝꿍", true, null, null)));
        when(reactionQuery.pick(anyLong(), anyString(), any())).thenReturn(Reaction.NONE);
        when(relationQuery.view(anyLong(), eq(CHARACTER_ID)))
                .thenReturn(new RelationView(CHARACTER_ID, 1, "단계1", 80, 0, null, 1, "단계1", 0, 3600));
    }

    // --- 대화 모드 · 크레딧 (20 · 28, 33 '크레딧 규칙') ---

    @Test
    void advancedModeChargesThirtyOnceAfterTheReply() throws Exception {
        UUID requestId = UUID.randomUUID();
        send(requestId, "이거 너무 어려워", "TEXT", "ADVANCED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("ADVANCED"))
                .andExpect(jsonPath("$.data.charged").value(30))
                .andExpect(jsonPath("$.data.credits.todayRemaining").value(1470)) // 출석 1,500 − 30
                .andExpect(jsonPath("$.data.safety").value(nullValue()))
                .andExpect(jsonPath("$.data.fallback").value(false));
        assertThat(jdbc.queryForObject(
                        "SELECT title FROM credit_ledger WHERE user_id = ? AND request_id = ?",
                        String.class,
                        userId,
                        requestId))
                .isEqualTo("고급 대화 · 채린");
        assertThat(jdbc.queryForObject(
                        "SELECT mode FROM chat_messages WHERE request_id = ? AND sender = 'CHARACTER'",
                        String.class,
                        requestId))
                .isEqualTo("ADVANCED");

        // 재전송: 다시 깎지 않고 처음과 같은 응답 (모드는 저장된 답장 기준, 요청 모드가 달라도)
        send(requestId, "이거 너무 어려워", "TEXT", "BASIC")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("ADVANCED"))
                .andExpect(jsonPath("$.data.charged").value(30))
                .andExpect(jsonPath("$.data.credits.todayRemaining").value(1470));
        assertThat(usedCount()).isEqualTo(1);
    }

    @Test
    void advancedModeStreamCarriesTheChargeInDone() throws Exception {
        String body = streamed(UUID.randomUUID(), "안녕", "ADVANCED");
        assertThat(body)
                .contains("event:done")
                .contains("\"mode\":\"ADVANCED\"")
                .contains("\"charged\":30")
                .contains("\"todayRemaining\":1470")
                .contains("\"safety\":null");
    }

    @Test
    void advancedModeWithoutEnoughCreditsIsRejectedAndSavesNothing() throws Exception {
        send(UUID.randomUUID(), "처음", "TEXT", "ADVANCED").andExpect(status().isOk()); // 출석분 지급 + 30
        jdbc.update("UPDATE credit_grants SET remaining = least(remaining, 29) WHERE user_id = ?", userId);
        clearInvocations();

        UUID rejected = UUID.randomUUID();
        send(rejected, "고급으로 물어볼래", "TEXT", "ADVANCED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREDIT_INSUFFICIENT"))
                .andExpect(jsonPath("$.detail.credits.todayRemaining").value(29));
        assertThat(rows(rejected)).isZero();
        verify(llm, never()).complete(eq(LlmPurpose.CHAT), any(), any(), any(), any());

        // 기본 모드로 바꿔 다시 보내면 된다 (크레딧을 보지도 깎지도 않음)
        send(rejected, "고급으로 물어볼래", "TEXT", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("BASIC"))
                .andExpect(jsonPath("$.data.charged").value(0))
                .andExpect(jsonPath("$.data.credits").value(nullValue()));
        assertThat(usedCount()).isEqualTo(1);
    }

    @Test
    void advancedFallbackIsNotCharged() throws Exception {
        doAnswer(inv -> {
                    if (inv.getArgument(0) == LlmPurpose.CHAT) throw new IllegalStateException("LLM 실패");
                    return inv.callRealMethod();
                })
                .when(llm)
                .complete(any(), any(), any(), any(), any());
        UUID requestId = UUID.randomUUID();

        send(requestId, "이거 어려워", "TEXT", "ADVANCED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fallback").value(true))
                .andExpect(jsonPath("$.data.mode").value("ADVANCED"))
                .andExpect(jsonPath("$.data.charged").value(0))
                .andExpect(jsonPath("$.data.credits.todayRemaining").value(1500));
        assertThat(usedCount()).isZero();
        // 재전송도 깎지 않은 그대로
        send(requestId, "이거 어려워", "TEXT", "ADVANCED")
                .andExpect(jsonPath("$.data.charged").value(0))
                .andExpect(jsonPath("$.data.credits.todayRemaining").value(1500));
    }

    // --- 음성 (28 '음성', ADR 0034). TTS는 가짜(무음 mp3) ---

    @Test
    void voiceStreamsAudioChunksThenAudioEndAndChargesTwenty() throws Exception {
        UUID requestId = UUID.randomUUID();
        String body = streamed(requestId, "안녕", null, true);

        assertThat(body).contains("event:audio").contains("\"format\":\"mp3\"").contains("\"seq\":0");
        assertThat(body).contains("\"voice\":true").contains("\"charged\":20").contains("\"todayRemaining\":1480");
        // audio_end는 맨 끝 한 번, done 뒤
        assertThat(body.indexOf("event:audio_end")).isGreaterThan(body.indexOf("event:done"));
        assertThat(body.trim()).contains("\"complete\":true");
        assertThat(body.split("event:audio_end", -1)).hasSize(2);
        assertThat(jdbc.queryForObject(
                        "SELECT title FROM credit_ledger WHERE user_id = ? AND request_id = ?",
                        String.class,
                        userId,
                        requestId))
                .isEqualTo("보이스 대화 · 채린");

        // 재전송은 음성 없이 done만, 깎은 금액은 처음과 같게
        String again = streamed(requestId, "안녕", null, true);
        assertThat(again)
                .doesNotContain("event:audio")
                .contains("\"voice\":false")
                .contains("\"charged\":20");
        assertThat(usedCount()).isEqualTo(1);
    }

    @Test
    void advancedVoiceChargesFiftyInOneLine() throws Exception {
        UUID requestId = UUID.randomUUID();
        assertThat(streamed(requestId, "안녕", "ADVANCED", true))
                .contains("\"voice\":true")
                .contains("\"charged\":50")
                .contains("\"todayRemaining\":1450");
        assertThat(jdbc.queryForObject(
                        "SELECT title FROM credit_ledger WHERE user_id = ? AND request_id = ?",
                        String.class,
                        userId,
                        requestId))
                .isEqualTo("고급 · 보이스 대화 · 채린");
    }

    @Test
    void voiceOffInSettingsOrShortOfCreditsMeansTextOnly() throws Exception {
        jdbc.update("UPDATE users SET settings = '{\"voiceEnabled\":false}'::jsonb WHERE id = ?", userId);
        assertThat(streamed(UUID.randomUUID(), "안녕", null, true))
                .doesNotContain("event:audio")
                .contains("\"voice\":false")
                .contains("\"charged\":0");

        jdbc.update("UPDATE users SET settings = '{}'::jsonb WHERE id = ?", userId);
        send(UUID.randomUUID(), "처음", "TEXT", null).andExpect(status().isOk());
        streamed(UUID.randomUUID(), "출석 지급", "ADVANCED", false); // 출석분 지급 (1,500 − 30)
        jdbc.update("UPDATE credit_grants SET remaining = 19 WHERE user_id = ?", userId);
        // 보이스 20보다 적으면 에러가 아니라 글만 (그 기능만 멈춤)
        assertThat(streamed(UUID.randomUUID(), "안녕", null, true))
                .doesNotContain("event:audio")
                .contains("\"voice\":false")
                .contains("event:done");
    }

    @Test
    void nonStreamingMessageNeverHasVoice() throws Exception {
        mvc.perform(post("/api/connections/{id}/messages", sessionId)
                        .with(jwt().jwt(j -> j.subject(String.valueOf(userId))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), "안녕", "TEXT", null, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.voice").value(false))
                .andExpect(jsonPath("$.data.audioBase64").value(nullValue()))
                .andExpect(jsonPath("$.data.charged").value(0));
    }

    @Test
    void fallbackLineIsVoicedButNotCharged() throws Exception {
        doAnswer(inv -> {
                    if (inv.getArgument(0) == LlmPurpose.CHAT) throw new IllegalStateException("LLM 실패");
                    return inv.callRealMethod();
                })
                .when(llm)
                .complete(any(), any(), any(), any(), any());
        assertThat(streamed(UUID.randomUUID(), "안녕", null, true))
                .contains("\"fallback\":true")
                .contains("event:audio")
                .contains("\"voice\":true")
                .contains("\"charged\":0")
                .contains("event:audio_end");
        assertThat(usedCount()).isZero();
    }

    @Test
    void creditsShortAfterVoiceStartedChargeOnlyWhatIsLeftAndResendMatches() throws Exception {
        send(UUID.randomUUID(), "처음", "TEXT", "ADVANCED").andExpect(status().isOk()); // 출석분 지급 + 30
        // 음성을 켠 뒤 LLM이 답하는 사이 다른 요청이 써서 35만 남음: 고급+보이스 50은 안 되고 고급 30만
        doAnswer(inv -> {
                    if (inv.getArgument(0) == LlmPurpose.CHAT)
                        jdbc.update("UPDATE credit_grants SET remaining = 35 WHERE user_id = ?", userId);
                    return inv.callRealMethod();
                })
                .when(llm)
                .complete(any(), any(), any(), any(), any());
        UUID both = UUID.randomUUID();
        assertThat(streamed(both, "고급 음성", "ADVANCED", true))
                .contains("\"voice\":true")
                .contains("\"charged\":30")
                .contains("event:audio_end");
        assertThat(streamed(both, "고급 음성", "ADVANCED", true)).contains("\"charged\":30"); // 재전송도 30

        // 보이스만인데 0이 됨: 깎지 않고 음성은 그대로 (그 기능만 멈춤), 재전송도 0
        doAnswer(inv -> {
                    if (inv.getArgument(0) == LlmPurpose.CHAT)
                        jdbc.update("UPDATE credit_grants SET remaining = 0 WHERE user_id = ?", userId);
                    return inv.callRealMethod();
                })
                .when(llm)
                .complete(any(), any(), any(), any(), any());
        jdbc.update("UPDATE credit_grants SET remaining = 100 WHERE user_id = ? AND remaining > 0", userId);
        UUID voiceOnly = UUID.randomUUID();
        assertThat(streamed(voiceOnly, "음성만", null, true))
                .contains("\"voice\":true")
                .contains("\"charged\":0");
        assertThat(streamed(voiceOnly, "음성만", null, true)).contains("\"charged\":0");
    }

    @Test
    void creditsRunningOutWhileAnsweringRollsBackTo409() throws Exception {
        send(UUID.randomUUID(), "처음", "TEXT", "ADVANCED").andExpect(status().isOk()); // 출석분 지급 + 30
        // 잔액을 본 뒤 LLM이 답하는 사이 다른 요청이 크레딧을 다 썼다
        doAnswer(inv -> {
                    if (inv.getArgument(0) == LlmPurpose.CHAT)
                        jdbc.update("UPDATE credit_grants SET remaining = 0 WHERE user_id = ?", userId);
                    return inv.callRealMethod();
                })
                .when(llm)
                .complete(any(), any(), any(), any(), any());
        UUID requestId = UUID.randomUUID();

        send(requestId, "고급으로 물어볼래", "TEXT", "ADVANCED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREDIT_INSUFFICIENT"))
                .andExpect(jsonPath("$.detail.credits.todayRemaining").value(0));
        assertThat(rows(requestId)).isZero(); // 답장도 사용자 말도 남기지 않는다
        assertThat(usedCount()).isEqualTo(1);
    }

    @Test
    void basicModeNeverTouchesCreditsAndVoiceIsStored() throws Exception {
        UUID requestId = UUID.randomUUID();
        send(requestId, "말로 한 말", "VOICE", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("BASIC"))
                .andExpect(jsonPath("$.data.charged").value(0))
                .andExpect(jsonPath("$.data.credits").value(nullValue()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM credit_grants WHERE user_id = ?", Integer.class, userId))
                .isZero(); // 보지도 않았다 (출석 지급도 없음)
        assertThat(jdbc.queryForObject(
                        "SELECT input_type FROM chat_messages WHERE request_id = ? AND sender = 'USER'",
                        String.class,
                        requestId))
                .isEqualTo("VOICE");
        assertThat(jdbc.queryForObject(
                        "SELECT mode FROM chat_messages WHERE request_id = ? AND sender = 'CHARACTER'",
                        String.class,
                        requestId))
                .isEqualTo("BASIC");

        // 32: inputType은 사용자 말에만
        room().andExpect(jsonPath("$.data.messages[0].sender").value("MATE"))
                .andExpect(jsonPath("$.data.messages[0].inputType").value(nullValue()))
                .andExpect(jsonPath("$.data.messages[1].inputType").value("VOICE"));
    }

    @Test
    void unknownInputTypeOrModeIsRejected() throws Exception {
        send(UUID.randomUUID(), "안녕", "TYPING", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        send(UUID.randomUUID(), "안녕", "TEXT", "TURBO")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // --- 안전 (20 '안전') ---

    @Test
    void violationGetsTheRefusalLineWithoutLlmAndThreeSuspend() throws Exception {
        when(safetyChecker.violation("나쁜 말")).thenReturn(Optional.of("TEST"));

        UUID first = UUID.randomUUID();
        send(first, "나쁜 말", "TEXT", "ADVANCED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value(REFUSAL))
                .andExpect(jsonPath("$.data.safety").value("WARNING"))
                .andExpect(jsonPath("$.data.fallback").value(false))
                .andExpect(jsonPath("$.data.charged").value(0)); // 안전 경고 턴은 안 깎음
        verify(llm, never()).complete(eq(LlmPurpose.CHAT), any(), any(), any(), any());
        // 호감도는 0점 (잠금 순서를 지키려 부르기는 한다)
        verify(relationCommands).applyChatScore(userId, CHARACTER_ID, sessionId, first, ChatScore.none());
        // 재전송도 경고 턴 그대로, 기록은 한 번
        send(first, "나쁜 말", "TEXT", "ADVANCED")
                .andExpect(jsonPath("$.data.safety").value("WARNING"))
                .andExpect(jsonPath("$.data.charged").value(0));
        assertThat(strikes()).isEqualTo(1);
        assertThat(usedCount()).isZero();

        send(UUID.randomUUID(), "나쁜 말", "TEXT", null)
                .andExpect(jsonPath("$.data.safety").value("WARNING"));
        send(UUID.randomUUID(), "괜찮은 말", "TEXT", null)
                .andExpect(jsonPath("$.data.safety").value(nullValue()));
        send(UUID.randomUUID(), "나쁜 말", "TEXT", null)
                .andExpect(jsonPath("$.data.safety").value("WARNING"));
        assertThat(strikes()).isEqualTo(3);

        // 24시간 안 3번 → 20 · 28 모두 403 (아무것도 저장하지 않음)
        UUID blocked = UUID.randomUUID();
        send(blocked, "괜찮은 말", "TEXT", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHAT_SUSPENDED"))
                .andExpect(jsonPath("$.detail.until").exists());
        assertThat(rows(blocked)).isZero();
        mvc.perform(post("/api/connections/{id}/messages/stream", sessionId)
                        .with(jwt().jwt(j -> j.subject(String.valueOf(userId))))
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), "괜찮은 말", "TEXT", null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHAT_SUSPENDED"));
        // 이미 답한 요청의 재전송은 그대로 받는다
        send(first, "나쁜 말", "TEXT", "ADVANCED").andExpect(status().isOk());
    }

    @Test
    void violationTurnsStayOutOfTheNextPrompt() throws Exception {
        when(safetyChecker.violation("나쁜 말")).thenReturn(Optional.of("TEST"));
        send(UUID.randomUUID(), "나쁜 말", "TEXT", null).andExpect(status().isOk());
        send(UUID.randomUUID(), "다음 말", "TEXT", null).andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Llm.ChatTurn>> turns = ArgumentCaptor.forClass(List.class);
        verify(llm).complete(eq(LlmPurpose.CHAT), any(), turns.capture(), any(), any());
        assertThat(turns.getValue())
                .extracting(Llm.ChatTurn::text)
                .containsExactly("다음 말"); // 위반한 말 · 거절 대사는 LLM에 넘기지 않는다
    }

    @Test
    void suspensionEndsTwentyFourHoursAfterTheLastStrike() throws Exception {
        // 3번 다 24시간 안이었지만 마지막 위반이 25시간 전 → 이미 풀림
        for (int hoursAgo : new int[] {27, 26, 25}) {
            strikeAt(hoursAgo);
        }
        send(UUID.randomUUID(), "안녕", "TEXT", null).andExpect(status().isOk());

        // 23시간 전 위반이 있었다면 그 시점까지 24시간 안 4번 → 그때부터 24시간(1시간 뒤까지) 정지
        strikeAt(23);
        send(UUID.randomUUID(), "안녕", "TEXT", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHAT_SUSPENDED"));
    }

    @Test
    void strikesSpreadOverMoreThanADayDoNotSuspend() throws Exception {
        strikeAt(40);
        strikeAt(20);
        strikeAt(1);
        send(UUID.randomUUID(), "안녕", "TEXT", null).andExpect(status().isOk());
    }

    // --- 40 답변 평가 ---

    @Test
    void ratingOverwritesAndCancels() throws Exception {
        UUID requestId = UUID.randomUUID();
        send(requestId, "파이팅", "QUICK", null).andExpect(status().isOk());

        rate(requestId, "{\"rating\":\"UP\"}").andExpect(status().isNoContent());
        assertThat(rating(requestId)).isEqualTo("UP");
        room().andExpect(jsonPath("$.data.messages[0].rating").value("UP"))
                .andExpect(jsonPath("$.data.messages[1].rating").value(nullValue()));
        rate(requestId, "{\"rating\":\"DOWN\"}").andExpect(status().isNoContent());
        assertThat(rating(requestId)).isEqualTo("DOWN");
        rate(requestId, "{\"rating\":null}").andExpect(status().isNoContent());
        assertThat(rating(requestId)).isNull();

        rate(requestId, "{\"rating\":\"MEH\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        rate(UUID.randomUUID(), "{\"rating\":\"UP\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MESSAGE_NOT_FOUND"));
    }

    @Test
    void othersRepliesCannotBeRatedOrReported() throws Exception {
        UUID requestId = UUID.randomUUID();
        send(requestId, "파이팅", "QUICK", null).andExpect(status().isOk());
        long other = newUser();

        mvc.perform(put("/api/messages/{id}/rating", requestId)
                        .with(jwt().jwt(j -> j.subject(String.valueOf(other))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"UP\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MESSAGE_NOT_FOUND"));
        mvc.perform(post("/api/messages/{id}/report", requestId)
                        .with(jwt().jwt(j -> j.subject(String.valueOf(other))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"VIOLENT\"}"))
                .andExpect(status().isNotFound());
        assertThat(rating(requestId)).isNull();
    }

    // --- 41 답변 신고 ---

    @Test
    void reportHidesTheReplyFromTheRoomAndTheNextPrompt() throws Exception {
        UUID requestId = UUID.randomUUID();
        send(requestId, "파이팅", "QUICK", null).andExpect(status().isOk());

        report(requestId, "{\"reason\":\"OTHER\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST")); // OTHER는 detail 필수
        report(requestId, "{\"reason\":\"OTHER\",\"detail\":\"" + "가".repeat(301) + "\"}")
                .andExpect(status().isBadRequest());
        report(requestId, "{\"reason\":\"BORING\"}").andExpect(status().isBadRequest());

        report(requestId, "{\"reason\":\"OTHER\",\"detail\":\"갑자기 말투가 바뀌었어요\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reportId").isNumber());
        report(requestId, "{\"reason\":\"INAPPROPRIATE\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REPORTED"));
        assertThat(jdbc.queryForObject(
                        "SELECT reason || '/' || detail || '/' || status FROM chat_reports WHERE user_id = ?",
                        String.class,
                        userId))
                .isEqualTo("OTHER/갑자기 말투가 바뀌었어요/OPEN");

        // 32: 신고한 답장은 빠지고 사용자 말은 남는다
        room().andExpect(jsonPath("$.data.messages.length()").value(1))
                .andExpect(jsonPath("$.data.messages[0].sender").value("USER"));

        // 다음 답장의 이전 대화에서도 빠진다
        clearInvocations();
        send(UUID.randomUUID(), "다음 말", "TEXT", null).andExpect(status().isOk());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Llm.ChatTurn>> turns = ArgumentCaptor.forClass(List.class);
        verify(llm, atLeastOnce()).complete(eq(LlmPurpose.CHAT), any(), turns.capture(), any(), any());
        assertThat(turns.getValue())
                .extracting(Llm.ChatTurn::text)
                .contains("파이팅", "다음 말")
                .noneMatch(t -> t.contains("「파이팅」"));
    }

    // --- 도우미 ---

    private void clearInvocations() {
        org.mockito.Mockito.clearInvocations(llm);
    }

    private long newUser() {
        return jdbc.queryForObject(
                "INSERT INTO users (nickname, email, password) VALUES ('테스트', ?, 'x') RETURNING id",
                Long.class,
                UUID.randomUUID() + "@dialogue-mode.test");
    }

    private void strikeAt(int hoursAgo) {
        jdbc.update(
                "INSERT INTO safety_strikes (user_id, connection_id, request_id, category, created_at)"
                        + " VALUES (?, ?, ?, 'TEST', now() - make_interval(hours => ?))",
                userId,
                sessionId,
                UUID.randomUUID(),
                hoursAgo);
    }

    private static String body(UUID requestId, String content, String inputType, String mode) {
        return body(requestId, content, inputType, mode, false);
    }

    private static String body(UUID requestId, String content, String inputType, String mode, boolean tts) {
        return "{\"requestId\":\"" + requestId + "\",\"characterId\":" + CHARACTER_ID + ",\"content\":\"" + content
                + "\",\"inputType\":\"" + inputType + "\"" + (mode == null ? "" : ",\"mode\":\"" + mode + "\"")
                + (tts ? ",\"tts\":true" : "") + "}";
    }

    private ResultActions send(UUID requestId, String content, String inputType, String mode) throws Exception {
        return mvc.perform(post("/api/connections/{id}/messages", sessionId)
                .with(jwt().jwt(j -> j.subject(String.valueOf(userId))))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(requestId, content, inputType, mode)));
    }

    private String streamed(UUID requestId, String content, String mode) throws Exception {
        return streamed(requestId, content, mode, false);
    }

    private String streamed(UUID requestId, String content, String mode, boolean tts) throws Exception {
        MvcResult started = mvc.perform(post("/api/connections/{id}/messages/stream", sessionId)
                        .with(jwt().jwt(j -> j.subject(String.valueOf(userId))))
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(requestId, content, "TEXT", mode, tts)))
                .andExpect(request().asyncStarted())
                .andReturn();
        mvc.perform(asyncDispatch(started)).andExpect(status().isOk());
        return started.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private ResultActions rate(UUID requestId, String json) throws Exception {
        return mvc.perform(put("/api/messages/{id}/rating", requestId)
                .with(jwt().jwt(j -> j.subject(String.valueOf(userId))))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions report(UUID requestId, String json) throws Exception {
        return mvc.perform(post("/api/messages/{id}/report", requestId)
                .with(jwt().jwt(j -> j.subject(String.valueOf(userId))))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions room() throws Exception {
        return mvc.perform(get("/api/mates/{id}/messages", CHARACTER_ID)
                        .with(jwt().jwt(j -> j.subject(String.valueOf(userId)))))
                .andExpect(status().isOk());
    }

    private int rows(UUID requestId) {
        return jdbc.queryForObject("SELECT count(*) FROM chat_messages WHERE request_id = ?", Integer.class, requestId);
    }

    private String rating(UUID requestId) {
        return jdbc.queryForObject(
                "SELECT rating FROM chat_messages WHERE request_id = ? AND sender = 'CHARACTER'",
                String.class,
                requestId);
    }

    private int usedCount() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM credit_ledger WHERE user_id = ? AND type = 'USE'", Integer.class, userId);
    }

    private int strikes() {
        return jdbc.queryForObject("SELECT count(*) FROM safety_strikes WHERE user_id = ?", Integer.class, userId);
    }
}
