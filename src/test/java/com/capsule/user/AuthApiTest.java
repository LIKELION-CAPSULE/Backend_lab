package com.capsule.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

class AuthApiTest extends UserApiTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void emailSignupLoginRefreshLogout() throws Exception {
        mvc.perform(get("/api/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.terms[0].termVersionId").value(3))
                .andExpect(jsonPath("$.data.terms[0].code").value("SERVICE"))
                .andExpect(jsonPath("$.data.terms[0].required").value(true))
                .andExpect(jsonPath("$.data.terms[1].termVersionId").value(4))
                .andExpect(jsonPath("$.data.terms[1].content").isString());

        String email = uniqueEmail();
        postJson("/api/auth/email/verification", "{\"email\":\"%s\"}".formatted(email.toUpperCase()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300));
        postJson("/api/auth/email/verification", "{\"email\":\"%s\"}".formatted(email))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        String code = emailSender.lastCode(email).orElseThrow();
        postJson(
                        "/api/auth/email/verification/confirm",
                        "{\"email\":\"%s\",\"code\":\"%s\"}".formatted(email, wrong(code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CODE"))
                .andExpect(jsonPath("$.detail.remainingAttempts").value(4)); // 5번 중 1번 틀림 (SCR-51)
        MvcResult confirmed = postJson(
                        "/api/auth/email/verification/confirm",
                        "{\"email\":\"%s\",\"code\":\"%s\"}".formatted(email, code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationToken").value(startsWith("vt_")))
                .andReturn();

        MvcResult signedUp = signup(read(confirmed, "$.data.verificationToken"), PASSWORD, "승희", "[3,4]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.onboardingRequired").value(true))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))
                .andReturn();
        int userId = read(signedUp, "$.data.userId");

        postJson("/api/auth/email/verification", "{\"email\":\"%s\"}".formatted(email))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));

        MvcResult loggedIn = postJson(
                        "/api/auth/email/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.onboardingRequired").value(true))
                .andReturn();
        Cookie oldRefresh = refreshCookie(loggedIn);

        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(oldRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andReturn();
        Cookie newRefresh = refreshCookie(refreshed);
        assertThat(newRefresh.getValue()).isNotEqualTo(oldRefresh.getValue());
        mvc.perform(post("/api/auth/refresh").cookie(oldRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));

        mvc.perform(post("/api/auth/logout").cookie(newRefresh).header(HttpHeaders.AUTHORIZATION, bearer(refreshed)))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        mvc.perform(post("/api/auth/refresh").cookie(newRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void verificationRejectsBadEmailAndTooManyWrongCodes() throws Exception {
        postJson("/api/auth/email/verification", "{\"email\":\"not-an-email\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL"));

        String email = uniqueEmail();
        postJson("/api/auth/email/verification", "{\"email\":\"%s\"}".formatted(email))
                .andExpect(status().isAccepted());
        String code = emailSender.lastCode(email).orElseThrow();
        String wrongBody = "{\"email\":\"%s\",\"code\":\"%s\"}".formatted(email, wrong(code));
        for (int i = 0; i < 4; i++) {
            postJson("/api/auth/email/verification/confirm", wrongBody)
                    .andExpect(jsonPath("$.code").value("INVALID_CODE"))
                    .andExpect(jsonPath("$.detail.remainingAttempts").value(4 - i));
        }
        postJson("/api/auth/email/verification/confirm", wrongBody)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
        postJson("/api/auth/email/verification/confirm", "{\"email\":\"%s\",\"code\":\"%s\"}".formatted(email, code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
    }

    @Test
    void signupValidatesTermsNicknamePasswordAndConsumesToken() throws Exception {
        String token = verificationToken(uniqueEmail());

        signup(token, PASSWORD, "승희", "[3]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TERMS_REQUIRED"));
        signup(token, PASSWORD, "   ", "[3,4]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_NICKNAME"));
        signup(token, PASSWORD, "가".repeat(21), "[3,4]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_NICKNAME"));
        signup(token, "short", "승희", "[3,4]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));

        signup(token, PASSWORD, " 승희 ", "[3,4]").andExpect(status().isCreated());
        signup(token, PASSWORD, "승희", "[3,4]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_EXPIRED"));
    }

    @Test
    void loginLocksAfterFiveFailures() throws Exception {
        String email = uniqueEmail();
        signedUp(email);
        String wrongBody = "{\"email\":\"%s\",\"password\":\"wrong-password\"}".formatted(email);

        for (int i = 0; i < 4; i++) {
            postJson("/api/auth/email/login", wrongBody)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        postJson("/api/auth/email/login", wrongBody)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
        postJson("/api/auth/email/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
    }

    @Test
    void kakaoSignsUpOnceThenLogsIn() throws Exception {
        String kakaoId = UUID.randomUUID().toString();
        String code = "fake-kakao:%s:카카오닉네임".formatted(kakaoId);

        // 새 사용자는 6에서 계정을 만들지 않고 가입 토큰만 받는다 (api.md 6)
        long usersBefore = jdbc.queryForObject("SELECT count(*) FROM users", Long.class);
        String signupToken = kakaoSignupToken(code);
        assertThat(signupToken).startsWith("kt_");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Long.class))
                .isEqualTo(usersBefore);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM user_social_accounts WHERE provider_user_id = ?", Long.class, kakaoId))
                .isZero();

        // 약관 동의와 함께 49에서 가입하면 보낸 약관(3·4)이 동의로 기록된다
        MvcResult created = kakaoSignup(signupToken, "[3,4]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andExpect(jsonPath("$.data.onboardingRequired").value(true))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")))
                .andReturn();
        int userId = read(created, "$.data.userId");
        assertThat(jdbc.queryForList(
                        "SELECT term_version_id FROM user_terms WHERE user_id = ? ORDER BY term_version_id",
                        Long.class,
                        userId))
                .containsExactly(3L, 4L);
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(created)))
                .andExpect(jsonPath("$.data.nickname").value("카카오닉네임"))
                .andExpect(jsonPath("$.data.email").value(nullValue()));

        // 가입 토큰은 한 번만
        kakaoSignup(signupToken, "[3,4]")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGNUP_TOKEN_INVALID"));

        postJson("/api/auth/social/KAKAO", kakaoBody(code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(false))
                .andExpect(jsonPath("$.data.userId").value(userId));

        postJson("/api/auth/social/KAKAO", kakaoBody("expired-code"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SOCIAL_AUTH_FAILED"));
    }

    @Test
    void unknownSocialProviderWithoutTokenIs401() throws Exception {
        // 공통 규칙: 토큰 없이 없는 경로를 부르면 404보다 401이 먼저
        postJson("/api/auth/social/GOOGLE", kakaoBody("fake-kakao:x:y"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void kakaoIgnoresLegacyTermsField() throws Exception {
        // 예전 프론트처럼 6에 약관을 보내도 가입되지 않고 가입 토큰을 받는다
        String code = "fake-kakao:%s:옛프론트".formatted(UUID.randomUUID());
        postJson("/api/auth/social/KAKAO", """
                        {"code":"%s","redirectUri":"http://localhost/oauth/kakao","agreedTermVersionIds":[3,4],
                         "deviceHash":"%s"}
                        """.formatted(code, deviceHash()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("KAKAO_SIGNUP_REQUIRED"));
    }

    @Test
    void kakaoSignupRejectsBadRequestsWithoutSpendingToken() throws Exception {
        String signupToken = kakaoSignupToken("fake-kakao:%s:약관".formatted(UUID.randomUUID()));

        // 필수 약관이 빠졌거나 최신 버전이 아님 → 400. 토큰은 남아 다시 보낼 수 있다
        kakaoSignup(signupToken, "[3]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TERMS_REQUIRED"));
        kakaoSignup(signupToken, "[]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TERMS_REQUIRED"));
        kakaoSignup(signupToken, "[3,999]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TERMS_REQUIRED"));
        postJson("/api/auth/social/KAKAO/signup", """
                        {"signupToken":"%s","agreedTermVersionIds":[3,4]}
                        """.formatted(signupToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        kakaoSignup(signupToken, "[3,4]").andExpect(status().isOk());
    }

    @Test
    void kakaoSignupRejectsUnknownToken() throws Exception {
        kakaoSignup("kt_forged", "[3,4]")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGNUP_TOKEN_INVALID"));
        postJson("/api/auth/social/KAKAO/signup", "{\"agreedTermVersionIds\":[3,4],\"deviceHash\":\"d\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGNUP_TOKEN_INVALID"));
    }

    @Test
    void secondSignupTokenOfSameKakaoAccountLogsIn() throws Exception {
        // 동의 화면을 두 번 띄워 토큰을 두 개 받은 경우: 늦은 쪽은 가입하지 않고 로그인
        String code = "fake-kakao:%s:두번".formatted(UUID.randomUUID());
        String first = kakaoSignupToken(code);
        String second = kakaoSignupToken(code);
        MvcResult created = kakaoSignup(first, "[3,4]")
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andReturn();
        kakaoSignup(second, "[3,4]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(false))
                .andExpect(jsonPath("$.data.userId").value((int) read(created, "$.data.userId")));
    }

    private static String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    private static Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("refresh_token");
        assertThat(cookie).isNotNull();
        return new Cookie(cookie.getName(), cookie.getValue());
    }
}
