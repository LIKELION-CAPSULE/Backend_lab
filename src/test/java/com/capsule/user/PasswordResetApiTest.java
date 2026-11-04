package com.capsule.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** api.md 25 · 26 비밀번호 찾기 (메일 링크) */
class PasswordResetApiTest extends UserApiTestSupport {

    private static final String NEW_PASSWORD = "newpass1234";

    @Test
    void resetByMailLink_thenOldPasswordAndOtherDevicesAreOut() throws Exception {
        String email = uniqueEmail();
        MvcResult signedUp = signedUp(email);
        Cookie otherDevice = refreshCookie(signedUp); // 비밀번호를 바꾸기 전에 로그인해 둔 다른 기기

        requestReset(email).andExpect(status().isAccepted());
        String link = sentLink(email);
        assertThat(link).startsWith("http://localhost:3000/password/reset?token=pr_");
        String token = link.substring(link.indexOf("token=") + "token=".length());

        // 규칙에 안 맞는 비밀번호는 400이고, 토큰은 쓰지 않아서 다시 시도할 수 있다
        reset(token, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));

        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());

        // 토큰은 한 번만
        reset(token, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));

        login(email, PASSWORD).andExpect(status().isUnauthorized());
        MvcResult newLogin =
                login(email, NEW_PASSWORD).andExpect(status().isOk()).andReturn();

        // 다른 기기의 리프레시 토큰은 무효 (모든 기기 로그아웃)
        mvc.perform(post("/api/auth/refresh").cookie(otherDevice)).andExpect(status().isUnauthorized());
        // 바꾼 뒤 새로 로그인한 세션은 그대로 갱신된다
        mvc.perform(post("/api/auth/refresh").cookie(refreshCookie(newLogin))).andExpect(status().isOk());
    }

    @Test
    void resetUnlocksLockedLogin() throws Exception {
        String email = uniqueEmail();
        signedUp(email);
        for (int i = 0; i < 5; i++) {
            login(email, "wrong-password");
        }
        login(email, PASSWORD).andExpect(status().isTooManyRequests()); // 5번 틀려서 잠김

        requestReset(email).andExpect(status().isAccepted());
        String link = sentLink(email);
        reset(link.substring(link.indexOf("token=") + "token=".length()), NEW_PASSWORD)
                .andExpect(status().isNoContent());

        login(email, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void unknownEmailGetsSameAnswerAndNoMail() throws Exception {
        String email = uniqueEmail();

        requestReset(email).andExpect(status().isAccepted());

        Thread.sleep(300); // 메일은 응답과 따로 나가므로 잠깐 기다린 뒤에도 없어야 한다
        assertThat(emailSender.lastResetLink(email)).isEmpty();
    }

    /** 메일은 응답과 따로(가상 스레드) 나가므로 도착할 때까지 잠깐 기다린다 */
    private String sentLink(String email) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            var link = emailSender.lastResetLink(email);
            if (link.isPresent()) {
                return link.get();
            }
            Thread.sleep(20);
        }
        throw new AssertionError("재설정 메일이 1초 안에 오지 않음");
    }

    @Test
    void badInputs() throws Exception {
        requestReset("not-an-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL"));
        reset("pr_nope", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));

        String email = uniqueEmail();
        requestReset(email).andExpect(status().isAccepted());
        requestReset(email)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    private org.springframework.test.web.servlet.ResultActions requestReset(String email) throws Exception {
        return postJson("/api/auth/password/reset-request", "{\"email\":\"%s\"}".formatted(email));
    }

    private org.springframework.test.web.servlet.ResultActions reset(String token, String newPassword)
            throws Exception {
        return postJson(
                "/api/auth/password/reset", "{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, newPassword));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return postJson("/api/auth/email/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    private static Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("refresh_token");
        assertThat(cookie).isNotNull();
        return new Cookie(cookie.getName(), cookie.getValue());
    }
}
