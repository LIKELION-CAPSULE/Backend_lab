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
                .andExpect(jsonPath("$.terms[0].termVersionId").value(3))
                .andExpect(jsonPath("$.terms[0].code").value("SERVICE"))
                .andExpect(jsonPath("$.terms[0].required").value(true))
                .andExpect(jsonPath("$.terms[1].termVersionId").value(4))
                .andExpect(jsonPath("$.terms[1].content").isString());

        String email = uniqueEmail();
        postJson("/api/auth/email/verification", "{\"email\":\"%s\"}".formatted(email.toUpperCase()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.expiresInSeconds").value(300));
        postJson("/api/auth/email/verification", "{\"email\":\"%s\"}".formatted(email))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        String code = emailSender.lastCode(email).orElseThrow();
        postJson(
                        "/api/auth/email/verification/confirm",
                        "{\"email\":\"%s\",\"code\":\"%s\"}".formatted(email, wrong(code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CODE"));
        MvcResult confirmed = postJson(
                        "/api/auth/email/verification/confirm",
                        "{\"email\":\"%s\",\"code\":\"%s\"}".formatted(email, code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationToken").value(startsWith("vt_")))
                .andReturn();

        MvcResult signedUp = signup(read(confirmed, "$.verificationToken"), PASSWORD, "승희", "[3,4]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.onboardingRequired").value(true))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))
                .andReturn();
        int userId = read(signedUp, "$.userId");

        postJson("/api/auth/email/verification", "{\"email\":\"%s\"}".formatted(email))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));

        MvcResult loggedIn = postJson(
                        "/api/auth/email/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.onboardingRequired").value(true))
                .andReturn();
        Cookie oldRefresh = refreshCookie(loggedIn);

        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(oldRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
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
                    .andExpect(jsonPath("$.code").value("INVALID_CODE"));
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

        // 약관 체크 없이 가입되고, 최신 필수 약관(3·4)이 동의로 기록된다 (api.md 6, 안내 문구 고지)
        MvcResult created = postJson("/api/auth/social/KAKAO", kakaoBody(code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewUser").value(true))
                .andExpect(jsonPath("$.onboardingRequired").value(true))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=")))
                .andReturn();
        int userId = read(created, "$.userId");
        assertThat(jdbc.queryForList(
                        "SELECT term_version_id FROM user_terms WHERE user_id = ? ORDER BY term_version_id",
                        Long.class,
                        userId))
                .containsExactly(3L, 4L);
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(created)))
                .andExpect(jsonPath("$.nickname").value("카카오닉네임"))
                .andExpect(jsonPath("$.email").value(nullValue()));

        postJson("/api/auth/social/KAKAO", kakaoBody(code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewUser").value(false))
                .andExpect(jsonPath("$.userId").value(userId));

        postJson("/api/auth/social/KAKAO", kakaoBody("expired-code"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SOCIAL_AUTH_FAILED"));
    }

    @Test
    void kakaoIgnoresLegacyTermsField() throws Exception {
        String code = "fake-kakao:%s:옛프론트".formatted(UUID.randomUUID());
        postJson("/api/auth/social/KAKAO", """
                        {"code":"%s","redirectUri":"http://localhost/oauth/kakao","agreedTermVersionIds":[3,4]}
                        """.formatted(code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewUser").value(true));
    }

    private static String kakaoBody(String code) {
        return """
                {"code":"%s","redirectUri":"http://localhost/oauth/kakao"}
                """.formatted(code);
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
