package com.capsule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.capsule.user.internal.FakeEmailSender;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

    @Test
    void 흐름1_이메일_가입부터_리포트까지() throws Exception {
        String email = "e2e-" + UUID.randomUUID() + "@example.com";

        // 1~4 약관 → 인증 → 가입
        MvcResult terms = call(get("/api/terms"), null, null, 200);
        List<Integer> termVersionIds = JsonPath.read(body(terms), "$.terms[*].termVersionId");
        assertThat(termVersionIds).contains(3, 4);

        call(post("/api/auth/email/verification"), null, "{\"email\":\"" + email + "\"}", 202);
        String code = emails.lastCode(email).orElseThrow();
        MvcResult confirm = call(
                post("/api/auth/email/verification/confirm"),
                null,
                "{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}",
                200);
        String verificationToken = JsonPath.read(body(confirm), "$.verificationToken");

        MvcResult signup = call(
                post("/api/auth/email/signup"),
                null,
                "{\"verificationToken\":\"" + verificationToken
                        + "\",\"password\":\"capsule1234\",\"nickname\":\"승희\",\"agreedTermVersionIds\":[3,4]}",
                201);
        String token = JsonPath.read(body(signup), "$.accessToken");
        assertThat((Boolean) JsonPath.read(body(signup), "$.onboardingRequired"))
                .isTrue();
        Cookie refresh = signup.getResponse().getCookie("refresh_token");
        assertThat(refresh).isNotNull();

        // 5~6 아바타 → 프로필 설정
        MvcResult avatars = call(get("/api/avatars"), token, null, 200);
        Integer avatarId = JsonPath.read(body(avatars), "$.avatars[0].id");
        MvcResult me = call(patch("/api/me"), token, "{\"nickname\":\"승희\",\"avatarId\":" + avatarId + "}", 200);
        assertThat((Boolean) JsonPath.read(body(me), "$.onboardingRequired")).isFalse();

        // 7~9 홈 → 룸 상세 → 일정
        MvcResult home = call(get("/api/home"), token, null, 200);
        assertThat((Object) JsonPath.read(body(home), "$.activeSession")).isNull();
        assertThat((Integer) JsonPath.read(body(home), "$.rooms[0].id")).isEqualTo(1);
        MvcResult room = call(get("/api/rooms/1"), token, null, 200);
        assertThat((Integer) JsonPath.read(body(room), "$.characters[0].relation.level"))
                .isEqualTo(1);
        String examDate = LocalDate.now().plusDays(10).toString();
        call(
                post("/api/me/memories"),
                token,
                "{\"type\":\"EXAM\",\"title\":\"영어 중간고사\",\"dueDate\":\"" + examDate + "\",\"dueTime\":null}",
                201);

        // 10~11 공부 시작 → FOCUS → 인사 반응
        MvcResult start =
                call(post("/api/sessions"), token, "{\"roomId\":1,\"characterId\":1,\"targetMinutes\":50}", 201);
        long sessionId = ((Number) JsonPath.read(body(start), "$.sessionId")).longValue();
        String s = "/api/sessions/" + sessionId;

        long focusId = event(token, s, "FOCUS", Instant.now().toString(), 201);
        MvcResult greeting = call(post(s + "/events/" + focusId + "/reaction"), token, reactionBody(null), 200);
        assertThat((String) JsonPath.read(body(greeting), "$.trigger")).isEqualTo("GREETING");
        assertThat((Integer) JsonPath.read(body(greeting), "$.affinity.delta")).isEqualTo(5);

        // 12 heartbeat
        call(post(s + "/heartbeat"), token, null, 204);

        // 13~14 자리 비움 → 반응 → 같은 이벤트 재전송
        String awayAt = Instant.now().toString();
        long awayId = event(token, s, "AWAY", awayAt, 201);
        MvcResult away = call(post(s + "/events/" + awayId + "/reaction"), token, reactionBody(1), 200);
        assertThat((String) JsonPath.read(body(away), "$.trigger")).isEqualTo("AWAY");
        assertThat((Integer) JsonPath.read(body(away), "$.affinity.delta")).isEqualTo(-1);
        assertThat(event(token, s, "AWAY", awayAt, 200)).isEqualTo(awayId);

        // 15 복귀
        long returnId = event(token, s, "RETURN", Instant.now().toString(), 201);
        MvcResult back = call(post(s + "/events/" + returnId + "/reaction"), token, reactionBody(null), 200);
        assertThat((Integer) JsonPath.read(body(back), "$.affinity.delta")).isEqualTo(1);

        // 16 채팅 → 같은 requestId 재전송은 한 번만
        String requestId = UUID.randomUUID().toString();
        String message = "{\"requestId\":\"" + requestId + "\",\"content\":\"이거 너무 어려워\",\"inputType\":\"QUICK\"}";
        MvcResult reply = call(post(s + "/messages"), token, message, 200);
        String replyContent = JsonPath.read(body(reply), "$.content");
        assertThat(replyContent).isNotBlank();
        MvcResult replyAgain = call(post(s + "/messages"), token, message, 200);
        assertThat((String) JsonPath.read(body(replyAgain), "$.content")).isEqualTo(replyContent);

        // 17 새로고침 → activeSession (H18 근거)
        MvcResult meNow = call(get("/api/me"), token, null, 200);
        assertThat(((Number) JsonPath.read(body(meNow), "$.activeSession.sessionId")).longValue())
                .isEqualTo(sessionId);

        // 18~20 종료 → 멱등 → 리포트
        MvcResult end = call(post(s + "/end"), token, "{\"studySeconds\":0,\"presentSeconds\":0}", 200);
        assertThat((Integer) JsonPath.read(body(end), "$.returnCount")).isEqualTo(1);
        int pointsBefore = JsonPath.read(body(end), "$.relation.pointsBefore");
        int pointsAfter = JsonPath.read(body(end), "$.relation.pointsAfter");
        assertThat(pointsAfter).isGreaterThan(pointsBefore);
        assertThat((String) JsonPath.read(body(end), "$.closingLine")).isNotBlank();
        MvcResult endAgain = call(post(s + "/end"), token, "{\"studySeconds\":0,\"presentSeconds\":0}", 200);
        assertThat(body(endAgain)).isEqualTo(body(end));
        MvcResult report = call(get(s + "/report"), token, null, 200);
        assertThat(body(report)).isEqualTo(body(end));

        // 21~22 갱신 (만료된 액세스 토큰이 헤더에 붙어 있어도 됨, 옛 리프레시는 401) → 로그아웃
        MvcResult refreshed = mvc.perform(post("/api/auth/refresh")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired.or.invalid")
                        .cookie(refresh))
                .andReturn();
        assertThat(refreshed.getResponse().getStatus()).as(body(refreshed)).isEqualTo(200);
        String newToken = JsonPath.read(body(refreshed), "$.accessToken");
        assertThat(mvc.perform(post("/api/auth/refresh").cookie(refresh))
                        .andReturn()
                        .getResponse()
                        .getStatus())
                .isEqualTo(401);
        Cookie newRefresh = refreshed.getResponse().getCookie("refresh_token");
        MvcResult logout = mvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken)
                        .cookie(newRefresh))
                .andReturn();
        assertThat(logout.getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void 흐름2_카카오_신규_가입() throws Exception {
        String code = "fake-kakao:e2e-" + UUID.randomUUID() + ":카카오닉";
        String noTerms = "{\"code\":\"" + code + "\",\"redirectUri\":\"http://localhost/oauth\"}";
        String withTerms =
                "{\"code\":\"" + code + "\",\"redirectUri\":\"http://localhost/oauth\",\"agreedTermVersionIds\":[3,4]}";

        MvcResult rejected = call(post("/api/auth/social/KAKAO"), null, noTerms, 400);
        assertThat((String) JsonPath.read(body(rejected), "$.code")).isEqualTo("TERMS_REQUIRED");

        MvcResult first = call(post("/api/auth/social/KAKAO"), null, withTerms, 200);
        assertThat((Boolean) JsonPath.read(body(first), "$.isNewUser")).isTrue();
        String token = JsonPath.read(body(first), "$.accessToken");
        MvcResult me = call(get("/api/me"), token, null, 200);
        assertThat((String) JsonPath.read(body(me), "$.nickname")).isEqualTo("카카오닉");

        MvcResult again = call(post("/api/auth/social/KAKAO"), null, withTerms, 200);
        assertThat((Boolean) JsonPath.read(body(again), "$.isNewUser")).isFalse();
    }

    @Test
    void 흐름3_비정상_종료_후_이어하기() throws Exception {
        String code = "fake-kakao:e2e-" + UUID.randomUUID() + ":이어하기";
        MvcResult login = call(
                post("/api/auth/social/KAKAO"),
                null,
                "{\"code\":\"" + code + "\",\"redirectUri\":\"http://localhost/oauth\",\"agreedTermVersionIds\":[3,4]}",
                200);
        String token = JsonPath.read(body(login), "$.accessToken");
        call(patch("/api/me"), token, "{\"nickname\":\"이어하기\"}", 200);

        MvcResult start =
                call(post("/api/sessions"), token, "{\"roomId\":1,\"characterId\":1,\"targetMinutes\":30}", 201);
        long sessionId = ((Number) JsonPath.read(body(start), "$.sessionId")).longValue();
        String s = "/api/sessions/" + sessionId;
        event(token, s, "FOCUS", Instant.now().toString(), 201);
        call(post(s + "/heartbeat"), token, null, 204);

        // 탭이 닫혀 3분 동안 heartbeat가 없었던 것으로 만든다
        jdbc.update(
                "UPDATE study_sessions SET started_at = started_at - interval '10 minutes',"
                        + " last_heartbeat_at = now() - interval '3 minutes' WHERE id = ?",
                sessionId);
        jdbc.update(
                "UPDATE session_events SET occurred_at = occurred_at - interval '10 minutes' WHERE session_id = ?",
                sessionId);

        MvcResult resumed = call(post(s + "/resume"), token, null, 200);
        assertThat((Integer) JsonPath.read(body(resumed), "$.breakSeconds")).isGreaterThan(0);

        call(post(s + "/end"), token, "{\"studySeconds\":60,\"presentSeconds\":60}", 200);
    }

    private long event(String token, String session, String type, String occurredAt, int status) throws Exception {
        MvcResult r = call(
                post(session + "/events"),
                token,
                "{\"type\":\"" + type + "\",\"occurredAt\":\"" + occurredAt + "\"}",
                status);
        return ((Number) JsonPath.read(body(r), "$.eventId")).longValue();
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
