package com.capsule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.capsule.dialogue.client.Decider;
import com.capsule.dialogue.service.ChatReplier;
import com.capsule.user.client.FakeEmailSender;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 전체 플로우 E2E (docs/specs/flow.md). 모든 모듈이 실제 빈으로 붙고, 외부 연동·LLM만 Fake.
 * 이 테스트가 깨지면 플로우가 끊긴 것. 테스트를 고쳐서 통과시키지 말 것(플로우가 실제로 바뀐 경우에만 명세와 함께 수정).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FlowE2ETest {

    @Autowired
    MockMvc mvc;

    @Autowired
    FakeEmailSender emails;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoSpyBean // 말의 종류는 결정 모델이 고른다 (명세 20). Fake는 늘 첫 보기(NEUTRAL)라 칭찬을 칭찬으로 고르게 한다
    Decider decider;

    @Test
    void 흐름1_이메일_가입부터_리포트까지() throws Exception {
        String email = "e2e-" + UUID.randomUUID() + "@example.com";

        // 1~4 약관 → 인증 → 가입
        MvcResult terms = call(get("/api/terms"), null, null, 200);
        List<Integer> termVersionIds = JsonPath.read(body(terms), "$.data.terms[*].termVersionId");
        assertThat(termVersionIds).contains(3, 4);

        call(post("/api/auth/email/verification"), null, "{\"email\":\"" + email + "\"}", 202);
        String code = emails.lastCode(email).orElseThrow();
        MvcResult confirm = call(
                post("/api/auth/email/verification/confirm"),
                null,
                "{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}",
                200);
        String verificationToken = JsonPath.read(body(confirm), "$.data.verificationToken");

        MvcResult signup = call(
                post("/api/auth/email/signup"),
                null,
                "{\"verificationToken\":\"" + verificationToken
                        + "\",\"password\":\"capsule1234\",\"nickname\":\"승희\",\"agreedTermVersionIds\":[3,4],"
                        + "\"birthDate\":\"2001-04-10\",\"deviceHash\":\"e2e-device\"}",
                201);
        String token = JsonPath.read(body(signup), "$.data.accessToken");
        assertThat((Boolean) JsonPath.read(body(signup), "$.data.onboardingRequired"))
                .isTrue();
        Cookie refresh = signup.getResponse().getCookie("refresh_token");
        assertThat(refresh).isNotNull();

        // 5~6 아바타 → 프로필 설정
        MvcResult avatars = call(get("/api/avatars"), token, null, 200);
        Integer avatarId = JsonPath.read(body(avatars), "$.data.avatars[0].id");
        // H02는 아바타만 보낸다. 닉네임은 가입 때 값 그대로 (api.md 10)
        MvcResult me = call(patch("/api/me"), token, "{\"avatarId\":" + avatarId + "}", 200);
        assertThat((Boolean) JsonPath.read(body(me), "$.data.onboardingRequired"))
                .isFalse();
        assertThat((String) JsonPath.read(body(me), "$.data.nickname")).isEqualTo("승희");

        // 7~9 홈 → 메이트 상세 → 일정
        MvcResult home = call(get("/api/home"), token, null, 200);
        assertThat((Object) JsonPath.read(body(home), "$.data.activeConnection"))
                .isNull();
        assertThat((Object) JsonPath.read(body(home), "$.data.lastMate")).isNull();
        assertThat((Integer) JsonPath.read(body(home), "$.data.mates[0].id")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(body(home), "$.data.todayConnectedSeconds"))
                .isZero();
        MvcResult mate = call(get("/api/mates/1"), token, null, 200);
        assertThat((String) JsonPath.read(body(mate), "$.data.model.format")).isEqualTo("CUBISM");
        assertThat((Boolean) JsonPath.read(body(mate), "$.data.hasConversation"))
                .isFalse();
        String examDate = LocalDate.now().plusDays(10).toString();
        call(
                post("/api/me/memories"),
                token,
                "{\"type\":\"EXAM\",\"title\":\"영어 중간고사\",\"dueDate\":\"" + examDate + "\",\"dueTime\":null}",
                201);

        // 10~11 연결 시작 → FOCUS → 인사 반응. 시드의 준비 중 메이트(2, 모델 없음)는 연결할 수 없다
        MvcResult comingSoon = call(post("/api/connections"), token, "{\"mateId\":2}", 409);
        assertThat((String) JsonPath.read(body(comingSoon), "$.code")).isEqualTo("MATE_COMING_SOON");
        MvcResult start = call(post("/api/connections"), token, "{\"mateId\":1}", 201);
        long sessionId = ((Number) JsonPath.read(body(start), "$.data.connectionId")).longValue();
        String s = "/api/connections/" + sessionId;

        // 연결 시작 응답의 인사: 시드 반응표 GREETING → 웃음, 첫 만남
        assertThat((Boolean) JsonPath.read(body(start), "$.data.firstMeet")).isTrue();
        // 크레딧 (7단계): 그날 출석 1,500 (내 정보에서 이미 받아서 한 번만)
        assertThat((Integer) JsonPath.read(body(start), "$.data.credits.todayRemaining"))
                .isEqualTo(1500);
        assertThat((String) JsonPath.read(body(start), "$.data.greeting.expression"))
                .isEqualTo("웃음");

        // 얼굴 감지 시작(PRESENT)은 반응 없음 (명세 18) → 19번도 부르지 않는다
        MvcResult present = eventCall(token, s, "PRESENT", Instant.now().toString(), 201);
        assertThat((Object) JsonPath.read(body(present), "$.data.reaction")).isNull();

        // 12 heartbeat → 청소년이 아니라 남은 시간 null
        MvcResult beat = call(
                post(s + "/heartbeat"),
                token,
                "{\"connectedSeconds\":0,\"presentSeconds\":0,\"cameraOnSeconds\":0,\"pipSeconds\":0}",
                200);
        assertThat((Object) JsonPath.read(body(beat), "$.data.minorRemainingSeconds"))
                .isNull();

        // 13~14 자리 비움 → 반응(표정 · 강도 1) → 같은 이벤트 재전송
        String awayAt = Instant.now().toString();
        MvcResult awayEvent = eventCall(token, s, "AWAY", awayAt, 201);
        assertThat((Integer) JsonPath.read(body(awayEvent), "$.data.reaction.intensity"))
                .isEqualTo(1);
        long awayId = eventId(awayEvent);
        MvcResult away = call(post(s + "/events/" + awayId + "/reaction"), token, reactionBody(1), 200);
        assertThat((String) JsonPath.read(body(away), "$.data.characters[0].content"))
                .startsWith("(가짜 반응)");
        MvcResult awayAgain = eventCall(token, s, "AWAY", awayAt, 200);
        assertThat(eventId(awayAgain)).isEqualTo(awayId);

        // 15 복귀 · 말 걸기
        MvcResult returned = eventCall(token, s, "RETURN", Instant.now().toString(), 201);
        call(post(s + "/events/" + eventId(returned) + "/reaction"), token, reactionBody(null), 200);
        MvcResult idle = eventCall(token, s, "IDLE_TALK", Instant.now().toString(), 201);
        assertThat((String) JsonPath.read(body(idle), "$.data.reaction.trigger"))
                .isEqualTo("IDLE");

        // 16 채팅 → 같은 requestId 재전송은 한 번만
        org.mockito.Mockito.doAnswer(inv -> ((Map<?, ?>) inv.getArgument(1)).containsKey(ChatReplier.QUESTION)
                        ? Map.of(ChatReplier.QUESTION, new Decider.Decision("COMPLIMENT", 0.9))
                        : inv.callRealMethod())
                .when(decider)
                .decide(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        String requestId = UUID.randomUUID().toString();
        String message = "{\"requestId\":\"" + requestId
                + "\",\"characterId\":1,\"content\":\"너 설명 진짜 멋있다\",\"inputType\":\"TEXT\"}";
        MvcResult reply = call(post(s + "/messages"), token, message, 200);
        assertThat(((Number) JsonPath.read(body(reply), "$.data.characterId")).longValue())
                .isEqualTo(1);
        String replyContent = JsonPath.read(body(reply), "$.data.content");
        assertThat(replyContent).isNotBlank();
        MvcResult replyAgain = call(post(s + "/messages"), token, message, 200);
        assertThat((String) JsonPath.read(body(replyAgain), "$.data.content")).isEqualTo(replyContent);

        // 17 새로고침 → activeConnection (이어하기 팝업 근거)
        MvcResult meNow = call(get("/api/me"), token, null, 200);
        assertThat((Integer) JsonPath.read(body(meNow), "$.data.credits.attendanceRemaining"))
                .isEqualTo(1500);
        assertThat(((Number) JsonPath.read(body(meNow), "$.data.activeConnection.connectionId")).longValue())
                .isEqualTo(sessionId);

        // 18~20 끊기 → 멱등 → 연결 기록. 1분 공부한 것으로 보내려면 실제 경과 시간이 1분 이상이어야 해서 시작 시각을 당긴다
        jdbc.update("UPDATE connections SET started_at = started_at - interval '2 minutes' WHERE id = ?", sessionId);
        String endBody = "{\"connectedSeconds\":60,\"presentSeconds\":60,\"cameraOnSeconds\":60,\"pipSeconds\":0}";
        MvcResult end = call(post(s + "/end"), token, endBody, 200);
        assertThat((Integer) JsonPath.read(body(end), "$.data.returnCount")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(body(end), "$.data.connectedSeconds"))
                .isEqualTo(60);
        // 친밀도 (2026-11-23): 대화가 한 턴 있어서 함께한 1분이 그대로 인정, 1단계 그대로 (2단계는 1시간)
        assertThat((Integer) JsonPath.read(body(end), "$.data.relation.stageBefore"))
                .isEqualTo(1);
        assertThat((Integer) JsonPath.read(body(end), "$.data.relation.stageAfter"))
                .isEqualTo(1);
        assertThat((Integer) JsonPath.read(body(end), "$.data.relation.creditedSeconds"))
                .isEqualTo(60);
        assertThat((Integer) JsonPath.read(body(end), "$.data.relation.nextStageSeconds"))
                .isEqualTo(3540);
        assertThat((Boolean) JsonPath.read(body(end), "$.data.relation.stageUp"))
                .isFalse();
        // 목표 시간이 없어져(2026-11-23) 공부 포기 대사는 안 나오고 완료 대사: 관계 단계 2 묶음의 첫 줄 (이 사용자의 첫 종료)
        assertThat((String) JsonPath.read(body(end), "$.data.closingLine"))
                .isEqualTo("오늘은 봐줄게 꽤 했으니까, 내일도 와 내가 이기는 거 구경해야지");
        // 대화방 (2026-11-23 6단계): 답장 한 번 = 대화 1, 재전송은 안 셈
        assertThat((Integer) JsonPath.read(body(end), "$.data.messageCount")).isEqualTo(1);
        MvcResult endAgain = call(post(s + "/end"), token, endBody, 200);
        assertThat(body(endAgain)).isEqualTo(body(end));
        // 끊어도 메시지가 남아 대화방 기록으로 보인다 (최신순: 답장 → 사용자 말), 카드에 넣은 표시만
        MvcResult room = call(get("/api/mates/1/messages?size=1"), token, null, 200);
        assertThat((String) JsonPath.read(body(room), "$.data.messages[0].sender"))
                .isEqualTo("MATE");
        assertThat((String) JsonPath.read(body(room), "$.data.messages[0].content"))
                .isEqualTo(replyContent);
        String cursor = JsonPath.read(body(room), "$.data.nextCursor");
        MvcResult older = call(get("/api/mates/1/messages?size=1&cursor=" + cursor), token, null, 200);
        assertThat((String) JsonPath.read(body(older), "$.data.messages[0].content"))
                .isEqualTo("너 설명 진짜 멋있다");
        assertThat((Object) JsonPath.read(body(older), "$.data.nextCursor")).isNull();
        call(get("/api/mates/1/messages?size=51"), token, null, 400);
        call(get("/api/mates/999/messages"), token, null, 404);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM chat_messages WHERE connection_id = ? AND recapped_at IS NOT NULL",
                        Integer.class,
                        sessionId))
                .isEqualTo(2);
        assertThat((Boolean) JsonPath.read(body(call(get("/api/mates/1"), token, null, 200)), "$.data.hasConversation"))
                .isTrue();
        MvcResult report = call(get(s), token, null, 200);
        assertThat(body(report)).isEqualTo(body(end));
        MvcResult homeAfter = call(get("/api/home"), token, null, 200);
        assertThat((Integer) JsonPath.read(body(homeAfter), "$.data.todayConnectedSeconds"))
                .isEqualTo(60);
        assertThat((Integer) JsonPath.read(body(homeAfter), "$.data.lastMate.id"))
                .isEqualTo(1);
        // 같은 사용자의 다음 종료는 다음 줄: 완료 대사는 연결이 아니라 사용자 단위로 돌려 쓴다
        MvcResult second = call(post("/api/connections"), token, "{\"mateId\":1}", 201);
        String s2 = "/api/connections/" + ((Number) JsonPath.read(body(second), "$.data.connectionId")).longValue();
        MvcResult end2 = call(
                post(s2 + "/end"),
                token,
                "{\"connectedSeconds\":0,\"presentSeconds\":0,\"cameraOnSeconds\":0,\"pipSeconds\":0}",
                200);
        assertThat((String) JsonPath.read(body(end2), "$.data.closingLine"))
                .isEqualTo("오늘 기록 나쁘지 않네, 근데 내 에세이가 한 줄 더 길어 이건 꼭 적어 둬");

        // 21~22 갱신 (만료된 액세스 토큰이 헤더에 붙어 있어도 됨, 옛 리프레시는 401) → 로그아웃
        MvcResult refreshed = mvc.perform(post("/api/auth/refresh")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired.or.invalid")
                        .cookie(refresh))
                .andReturn();
        assertThat(refreshed.getResponse().getStatus()).as(body(refreshed)).isEqualTo(200);
        String newToken = JsonPath.read(body(refreshed), "$.data.accessToken");
        assertThat(mvc.perform(post("/api/auth/refresh").cookie(refresh))
                        .andReturn()
                        .getResponse()
                        .getStatus())
                .isEqualTo(401);
        assertThat(newToken).isNotBlank();
        Cookie newRefresh = refreshed.getResponse().getCookie("refresh_token");
        // 액세스 토큰이 만료돼도(헤더 없이) 로그아웃은 리프레시 토큰을 폐기한다 (공용 시연 기기)
        MvcResult logout =
                mvc.perform(post("/api/auth/logout").cookie(newRefresh)).andReturn();
        assertThat(logout.getResponse().getStatus()).isEqualTo(204);
        assertThat(logout.getResponse().getCookie("refresh_token").getMaxAge()).isZero();
        assertThat(mvc.perform(post("/api/auth/refresh").cookie(newRefresh))
                        .andReturn()
                        .getResponse()
                        .getStatus())
                .isEqualTo(401);
    }

    @Test
    void 흐름2_카카오_신규_가입() throws Exception {
        String code = "fake-kakao:e2e-" + UUID.randomUUID() + ":카카오닉";
        String kakao = "{\"code\":\"" + code + "\",\"redirectUri\":\"http://localhost/oauth\",\"deviceHash\":\"e2e\"}";

        MvcResult first = call(post("/api/auth/social/KAKAO"), null, kakao, 200);
        assertThat((Boolean) JsonPath.read(body(first), "$.data.isNewUser")).isTrue();
        String token = JsonPath.read(body(first), "$.data.accessToken");
        MvcResult me = call(get("/api/me"), token, null, 200);
        assertThat((String) JsonPath.read(body(me), "$.data.nickname")).isEqualTo("카카오닉");
        // 없는 경로도 에러 응답 모양(B13) 그대로
        MvcResult missing = call(get("/api/nope"), token, null, 404);
        assertThat((String) JsonPath.read(body(missing), "$.code")).isEqualTo("NOT_FOUND");

        MvcResult again = call(post("/api/auth/social/KAKAO"), null, kakao, 200);
        assertThat((Boolean) JsonPath.read(body(again), "$.data.isNewUser")).isFalse();
    }

    @Test
    void 흐름3_비정상_종료_후_이어하기() throws Exception {
        String code = "fake-kakao:e2e-" + UUID.randomUUID() + ":이어하기";
        MvcResult login = call(
                post("/api/auth/social/KAKAO"),
                null,
                "{\"code\":\"" + code + "\",\"redirectUri\":\"http://localhost/oauth\",\"deviceHash\":\"e2e\"}",
                200);
        String token = JsonPath.read(body(login), "$.data.accessToken");
        // 카카오 가입은 생년월일을 온보딩에서 받는다 (명세 10). 없으면 공부 시작이 막힌다
        call(patch("/api/me"), token, "{\"nickname\":\"이어하기\",\"birthDate\":\"2001-04-10\"}", 200);

        MvcResult start = call(post("/api/connections"), token, "{\"mateId\":1}", 201);
        long sessionId = ((Number) JsonPath.read(body(start), "$.data.connectionId")).longValue();
        String s = "/api/connections/" + sessionId;
        event(token, s, "PRESENT", Instant.now().toString(), 201);
        // 10분 전에 시작해 7분째 heartbeat를 보내고 탭이 닫힌 것으로 만든다
        jdbc.update("UPDATE connections SET started_at = started_at - interval '10 minutes' WHERE id = ?", sessionId);
        String sevenMinutes =
                "{\"connectedSeconds\":420,\"presentSeconds\":400,\"cameraOnSeconds\":410,\"pipSeconds\":0}";
        call(post(s + "/heartbeat"), token, sevenMinutes, 200);
        jdbc.update("UPDATE connections SET last_heartbeat_at = now() - interval '3 minutes' WHERE id = ?", sessionId);

        // 이어하기는 마지막 heartbeat 값으로 타이머를 잇는다 (끊긴 3분은 안 셈)
        MvcResult resumed = call(post(s + "/resume"), token, null, 200);
        assertThat((Integer) JsonPath.read(body(resumed), "$.data.connectedSeconds"))
                .isEqualTo(420);
        assertThat((Integer) JsonPath.read(body(resumed), "$.data.presentSeconds"))
                .isEqualTo(400);

        call(post(s + "/end"), token, sevenMinutes, 200);
    }

    private long event(String token, String session, String type, String occurredAt, int status) throws Exception {
        return eventId(eventCall(token, session, type, occurredAt, status));
    }

    private MvcResult eventCall(String token, String session, String type, String occurredAt, int status)
            throws Exception {
        return call(
                post(session + "/events"),
                token,
                "{\"type\":\"" + type + "\",\"occurredAt\":\"" + occurredAt + "\"}",
                status);
    }

    private static long eventId(MvcResult r) throws Exception {
        return ((Number) JsonPath.read(body(r), "$.data.eventId")).longValue();
    }

    private static String reactionBody(Integer intensity) {
        return "{\"requestId\":\"" + UUID.randomUUID() + "\"" + (intensity == null ? "" : ",\"intensity\":" + intensity)
                + "}";
    }

    private MvcResult call(MockHttpServletRequestBuilder request, String token, String json, int expectedStatus)
            throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus())
                .as(
                        "%s %s → %s",
                        result.getRequest().getMethod(), result.getRequest().getRequestURI(), body(result))
                .isEqualTo(expectedStatus);
        return result;
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
