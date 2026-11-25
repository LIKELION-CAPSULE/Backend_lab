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

        // 새 사용자: 6은 계정을 만들지 않고 가입 토큰 (2026-11-26) → 49에서 약관 동의와 함께 가입 (api.md 6 · 49)
        String signupToken = kakaoSignupToken(code);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM user_social_accounts WHERE provider_user_id = ?", Integer.class, kakaoId))
                .isZero();
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

        postJson("/api/auth/social/KAKAO", kakaoBody(code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(false))
                .andExpect(jsonPath("$.data.userId").value(userId));

        postJson("/api/auth/social/KAKAO", kakaoBody("expired-code"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SOCIAL_AUTH_FAILED"));

        // 가입 토큰은 1회용
        kakaoSignup(signupToken, "[3,4]")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGNUP_TOKEN_EXPIRED"));
    }

    @Test
    void kakaoSignupChecksTermsBeforeUsingTheToken() throws Exception {
        String kakaoId = UUID.randomUUID().toString();
        String token = kakaoSignupToken("fake-kakao:%s:약관".formatted(kakaoId));
        // 6 응답: 10분짜리 토큰
        postJson("/api/auth/social/KAKAO", kakaoBody("fake-kakao:%s:약관".formatted(kakaoId)))
                .andExpect(jsonPath("$.detail.expiresInSeconds").value(600));

        // 필수 약관이 빠지면 400, 토큰은 그대로라 고쳐서 다시 보낼 수 있다
        kakaoSignup(token, "[3]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TERMS_REQUIRED"));
        kakaoSignup(token, "[3,4]").andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM user_social_accounts WHERE provider_user_id = ?", Integer.class, kakaoId))
                .isEqualTo(1);

        // 모양이 아닌 토큰 · 없는 토큰
        kakaoSignup("forged", "[3,4]")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGNUP_TOKEN_INVALID"));
        kakaoSignup("kst_nope", "[3,4]")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGNUP_TOKEN_EXPIRED"));
    }

    @Test
    void twoSignupTokensForOneKakaoUserMakeOneAccount() throws Exception {
        // 다른 탭에서 6을 두 번 불러 토큰이 두 개여도 계정은 하나 (늦은 쪽은 로그인)
        String code = "fake-kakao:%s:두탭".formatted(UUID.randomUUID());
        String a = kakaoSignupToken(code), b = kakaoSignupToken(code);
        MvcResult first = kakaoSignup(a, "[3,4]").andExpect(status().isOk()).andReturn();
        kakaoSignup(b, "[3,4]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(false))
                .andExpect(jsonPath("$.data.userId").value((Integer) read(first, "$.data.userId")));
    }

    @Test
    void unknownSocialProviderWithoutTokenIs401() throws Exception {
        // 공통 규칙: 토큰 없이 없는 경로를 부르면 404보다 401이 먼저
        postJson("/api/auth/social/GOOGLE", kakaoBody("fake-kakao:x:y"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void kakaoNewUserNeverSignsUpInStepSix() throws Exception {
        // 옛 프론트처럼 6에 약관을 같이 보내도 가입하지 않는다 (약관은 49에서만)
        String code = "fake-kakao:%s:옛프론트".formatted(UUID.randomUUID());
        postJson("/api/auth/social/KAKAO", """
                        {"code":"%s","redirectUri":"http://localhost/oauth/kakao","agreedTermVersionIds":[3,4],
                         "deviceHash":"%s"}
                        """.formatted(code, deviceHash()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("KAKAO_SIGNUP_REQUIRED"));
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
