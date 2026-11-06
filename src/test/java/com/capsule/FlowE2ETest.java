package com.capsule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.capsule.user.client.FakeEmailSender;
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
        // H02는 아바타만 보낸다. 닉네임은 가입 때 값 그대로 (api.md 10)
        MvcResult me = call(patch("/api/me"), token, "{\"avatarId\":" + avatarId + "}", 200);
        assertThat((Boolean) JsonPath.read(body(me), "$.onboardingRequired")).isFalse();
        assertThat((String) JsonPath.read(body(me), "$.nickname")).isEqualTo("승희");

        // 7~9 홈 → 룸 상세 → 일정
        MvcResult home = call(get("/api/home"), token, null, 200);
        assertThat((Object) JsonPath.read(body(home), "$.activeSession")).isNull();
        assertThat((Integer) JsonPath.read(body(home), "$.rooms[0].id")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(body(home), "$.todayStudySeconds")).isZero();
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

        MvcResult focus = eventCall(token, s, "FOCUS", Instant.now().toString(), 201);
        assertThat(affinityDelta(focus)).isZero(); // 연속 출석 1일째는 점수 없음 (2일째부터)
        assertThat((Integer) JsonPath.read(body(focus), "$.characters[0].affinity.points"))
                .isEqualTo(50); // 50에서 시작
        MvcResult greeting = call(post(s + "/events/" + eventId(focus) + "/reaction"), token, reactionBody(null), 200);
        // 상태 변화 반응은 LLM이 대사 + 표정 (ADR 0022 결정 18. 테스트는 가짜 AI)
        assertThat((String) JsonPath.read(body(greeting), "$.characters[0].content"))
                .startsWith("(가짜 반응)");
        assertThat((Boolean) JsonPath.read(body(greeting), "$.characters[0].fallback"))
                .isFalse();

        // 순간 동작은 캐릭터 설정(DB, 처음 값은 characters/malfoy/profile.yml)의 미리 쓴 대사 · 표정.
        // 호감도 50 = 관계 단계 2의 대사
        MvcResult drowsy = eventCall(token, s, "DROWSY", Instant.now().toString(), 201);
        MvcResult drowsyLine =
                call(post(s + "/events/" + eventId(drowsy) + "/reaction"), token, reactionBody(null), 200);
        assertThat((String) JsonPath.read(body(drowsyLine), "$.characters[0].content"))
                .isEqualTo("졸지 마, 너 졸면 내가 이겨도 이긴 게 아니잖아 정정당당하게 지라고");
        assertThat((String) JsonPath.read(body(drowsyLine), "$.characters[0].emotion"))
                .isEqualTo("경멸");

        // 12 heartbeat
        call(post(s + "/heartbeat"), token, "{\"studySeconds\":0,\"presentSeconds\":0}", 204);

        // 13~14 자리 비움 → 반응 → 같은 이벤트 재전송
        String awayAt = Instant.now().toString();
        MvcResult awayEvent = eventCall(token, s, "AWAY", awayAt, 201);
        assertThat(affinityDelta(awayEvent)).isZero(); // 자리 비움 감점은 끝날 때 1분 넘게 비웠으면
        long awayId = eventId(awayEvent);
        MvcResult away = call(post(s + "/events/" + awayId + "/reaction"), token, reactionBody(1), 200);
        assertThat((String) JsonPath.read(body(away), "$.characters[0].content"))
                .startsWith("(가짜 반응)");
        MvcResult awayAgain = eventCall(token, s, "AWAY", awayAt, 200);
        assertThat(eventId(awayAgain)).isEqualTo(awayId);
        assertThat(affinityDelta(awayAgain)).isZero(); // 재전송은 다시 반영하지 않고 같은 변화

        // 15 복귀: 1분 안에 돌아와서 감점 없음, 돌아옴 자체도 0점
        MvcResult returned = eventCall(token, s, "RETURN", Instant.now().toString(), 201);
        assertThat(affinityDelta(returned)).isZero();
        call(post(s + "/events/" + eventId(returned) + "/reaction"), token, reactionBody(null), 200);

        // 16 채팅 → 같은 requestId 재전송은 한 번만
        String requestId = UUID.randomUUID().toString();
        String message = "{\"requestId\":\"" + requestId
                + "\",\"characterId\":1,\"content\":\"너 설명 진짜 멋있다\",\"inputType\":\"TEXT\"}";
        MvcResult reply = call(post(s + "/messages"), token, message, 200);
        assertThat(((Number) JsonPath.read(body(reply), "$.characterId")).longValue())
                .isEqualTo(1);
        String replyContent = JsonPath.read(body(reply), "$.content");
        assertThat(replyContent).isNotBlank();
        MvcResult replyAgain = call(post(s + "/messages"), token, message, 200);
        assertThat((String) JsonPath.read(body(replyAgain), "$.content")).isEqualTo(replyContent);

        // 17 새로고침 → activeSession (H18 근거)
        MvcResult meNow = call(get("/api/me"), token, null, 200);
        assertThat(((Number) JsonPath.read(body(meNow), "$.activeSession.sessionId")).longValue())
                .isEqualTo(sessionId);

        // 18~20 종료 → 멱등 → 리포트. 1분 공부한 것으로 보내려면 실제 경과 시간이 1분 이상이어야 해서 시작 시각을 당긴다
        jdbc.update("UPDATE study_sessions SET started_at = started_at - interval '2 minutes' WHERE id = ?", sessionId);
        String endBody = "{\"studySeconds\":60,\"presentSeconds\":60}";
        MvcResult end = call(post(s + "/end"), token, endBody, 200);
        assertThat((Integer) JsonPath.read(body(end), "$.returnCount")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(body(end), "$.studySeconds")).isEqualTo(60);
        // 호감도 0~100, 50에서 시작: 연속 출석 1일째 0, 짧은 자리 비움 0, 복귀 0, 칭찬 +2, 공부 시간은 레벨에만
        assertThat((Integer) JsonPath.read(body(end), "$.relation.pointsBefore"))
                .isEqualTo(50);
        assertThat((Integer) JsonPath.read(body(end), "$.relation.pointsAfter")).isEqualTo(52);
        assertThat((String) JsonPath.read(body(end), "$.relation.stageName")).isEqualTo("친한 사이");
        assertThat((Integer) JsonPath.read(body(end), "$.relation.levelAfter")).isEqualTo(1); // 자리 지킴 1분
        // 목표 50분의 절반도 안 채우고 끝냄 → 말포이 공부 포기 대사, 관계 단계 2 묶음의 첫 줄 (이 사용자의 첫 종료)
        assertThat((String) JsonPath.read(body(end), "$.closingLine")).isEqualTo("목표 반도 못 채웠잖아, 내일은 나보다 먼저 일어나지 마");
        MvcResult endAgain = call(post(s + "/end"), token, endBody, 200);
        assertThat(body(endAgain)).isEqualTo(body(end));
        MvcResult report = call(get(s + "/report"), token, null, 200);
        assertThat(body(report)).isEqualTo(body(end));
        MvcResult roomAfter = call(get("/api/rooms/1"), token, null, 200);
        assertThat((Integer) JsonPath.read(body(roomAfter), "$.characters[0].relation.totalStudySeconds"))
                .isEqualTo(60);
        MvcResult homeAfter = call(get("/api/home"), token, null, 200);
        assertThat((Integer) JsonPath.read(body(homeAfter), "$.todayStudySeconds"))
                .isEqualTo(60);
        // 같은 사용자의 다음 종료는 다음 줄: 완료 · 포기 대사는 세션이 아니라 사용자 단위로 돌려 쓴다
        MvcResult second =
                call(post("/api/sessions"), token, "{\"roomId\":1,\"characterId\":1,\"targetMinutes\":50}", 201);
        String s2 = "/api/sessions/" + ((Number) JsonPath.read(body(second), "$.sessionId")).longValue();
        MvcResult end2 = call(post(s2 + "/end"), token, "{\"studySeconds\":0,\"presentSeconds\":0}", 200);
        assertThat((String) JsonPath.read(body(end2), "$.closingLine")).isEqualTo("오늘은 내 승리네, 억울하면 내일 다시 붙어 기다릴 테니까");

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
        String kakao = "{\"code\":\"" + code + "\",\"redirectUri\":\"http://localhost/oauth\"}";

        MvcResult first = call(post("/api/auth/social/KAKAO"), null, kakao, 200);
        assertThat((Boolean) JsonPath.read(body(first), "$.isNewUser")).isTrue();
        String token = JsonPath.read(body(first), "$.accessToken");
        MvcResult me = call(get("/api/me"), token, null, 200);
        assertThat((String) JsonPath.read(body(me), "$.nickname")).isEqualTo("카카오닉");
        // 없는 경로도 에러 응답 모양(B13) 그대로
        MvcResult missing = call(get("/api/nope"), token, null, 404);
        assertThat((String) JsonPath.read(body(missing), "$.code")).isEqualTo("NOT_FOUND");

        MvcResult again = call(post("/api/auth/social/KAKAO"), null, kakao, 200);
        assertThat((Boolean) JsonPath.read(body(again), "$.isNewUser")).isFalse();
    }

    @Test
    void 흐름3_비정상_종료_후_이어하기() throws Exception {
        String code = "fake-kakao:e2e-" + UUID.randomUUID() + ":이어하기";
        MvcResult login = call(
                post("/api/auth/social/KAKAO"),
                null,
                "{\"code\":\"" + code + "\",\"redirectUri\":\"http://localhost/oauth\"}",
                200);
        String token = JsonPath.read(body(login), "$.accessToken");
        call(patch("/api/me"), token, "{\"nickname\":\"이어하기\"}", 200);

        MvcResult start =
                call(post("/api/sessions"), token, "{\"roomId\":1,\"characterId\":1,\"targetMinutes\":30}", 201);
        long sessionId = ((Number) JsonPath.read(body(start), "$.sessionId")).longValue();
        String s = "/api/sessions/" + sessionId;
        event(token, s, "FOCUS", Instant.now().toString(), 201);
        call(post(s + "/heartbeat"), token, "{\"studySeconds\":0,\"presentSeconds\":0}", 204);

        // 탭이 닫혀 3분 동안 heartbeat가 없었던 것으로 만든다
        jdbc.update(
                "UPDATE study_sessions SET started_at = started_at - interval '10 minutes',"
                        + " last_heartbeat_at = now() - interval '3 minutes' WHERE id = ?",
                sessionId);
        jdbc.update(
                "UPDATE session_events SET occurred_at = occurred_at - interval '10 minutes' WHERE session_id = ?",
                sessionId);

        // 시작~마지막 heartbeat 7분은 공부, 끊긴 3분은 휴식 (DB와 앱 시계 차이만큼 여유)
        MvcResult resumed = call(post(s + "/resume"), token, null, 200);
        assertThat((Integer) JsonPath.read(body(resumed), "$.studySeconds")).isCloseTo(420, within(5));
        assertThat((Integer) JsonPath.read(body(resumed), "$.breakSeconds")).isCloseTo(180, within(5));

        call(post(s + "/end"), token, "{\"studySeconds\":60,\"presentSeconds\":60}", 200);
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
        return ((Number) JsonPath.read(body(r), "$.eventId")).longValue();
    }

    private static int affinityDelta(MvcResult r) throws Exception {
        return JsonPath.read(body(r), "$.characters[0].affinity.delta");
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
