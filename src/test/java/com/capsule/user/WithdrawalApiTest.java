package com.capsule.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.user.api.UserTypes.ActiveSessionSummary;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/** api.md 27 회원 탈퇴 */
class WithdrawalApiTest extends UserApiTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void resetSessionMock() {
        Mockito.reset(activeSessionLookup);
    }

    @Test
    void withdraw_clearsPersonalDataAndLogsOutEverywhere() throws Exception {
        String email = uniqueEmail();
        MvcResult signedUp = signedUp(email);
        String auth = bearer(signedUp);
        long userId = ((Number) read(signedUp, "$.userId")).longValue();
        Cookie refresh = refreshCookie(signedUp);

        mvc.perform(post("/api/me/memories")
                        .header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"type\":\"EXAM\",\"title\":\"중간고사\",\"dueDate\":\"2026-12-01\"}"))
                .andExpect(status().isCreated());
        long characterId = jdbc.queryForObject("SELECT id FROM characters ORDER BY id LIMIT 1", Long.class);
        long conversationId = jdbc.queryForObject(
                "INSERT INTO conversations (user_id, character_id) VALUES (?, ?) RETURNING id",
                Long.class,
                userId,
                characterId);
        jdbc.update(
                "INSERT INTO character_memories (user_id, character_id, content) VALUES (?, ?, '고3 수험생')",
                userId,
                characterId);
        jdbc.update(
                "INSERT INTO conversation_recaps (conversation_id, session_id, content) VALUES (?, 1, '카드')",
                conversationId);

        mvc.perform(delete("/api/me").header("Authorization", auth))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));

        // 행은 남고 개인정보만 비움
        var row = jdbc.queryForMap(
                "SELECT nickname, email, password, avatar_id, withdrawn_at FROM users WHERE id = ?", userId);
        assertThat(row.get("nickname")).isEqualTo("탈퇴한 사용자");
        assertThat(row.get("email")).isNull();
        assertThat(row.get("password")).isNull();
        assertThat(row.get("withdrawn_at")).isNotNull();
        // 직접 말한 내용 · 대화 기억 삭제, 동의 기록은 남김
        assertThat(count("user_memories", userId)).isZero();
        assertThat(count("conversations", userId)).isZero();
        assertThat(count("character_memories", userId)).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM conversation_recaps WHERE conversation_id = ?",
                        Integer.class,
                        conversationId))
                .isZero();
        assertThat(count("user_terms", userId)).isPositive();

        // 모든 기기 로그아웃, 남은 액세스 토큰으로는 401, 다시 불러도 204
        mvc.perform(post("/api/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", auth)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/me/memories")
                        .header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"type\":\"GOAL\",\"title\":\"다시\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(count("user_memories", userId)).isZero();
        mvc.perform(delete("/api/me").header("Authorization", auth)).andExpect(status().isNoContent());

        // 이메일이 비워져서 같은 이메일로 새로 가입할 수 있다 (인증 요청은 1분 제한이라 DB로 확인)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email))
                .isZero();
    }

    @Test
    void kakaoAccountCanSignUpAgainAsNewUser() throws Exception {
        String code = "fake-kakao:%s:카카오닉네임".formatted(UUID.randomUUID());
        MvcResult first = postJson("/api/auth/social/KAKAO", kakaoBody(code))
                .andExpect(jsonPath("$.isNewUser").value(true))
                .andReturn();

        mvc.perform(delete("/api/me").header("Authorization", bearer(first))).andExpect(status().isNoContent());

        MvcResult second = postJson("/api/auth/social/KAKAO", kakaoBody(code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewUser").value(true))
                .andReturn();
        assertThat(((Number) read(second, "$.userId")).longValue())
                .isNotEqualTo(((Number) read(first, "$.userId")).longValue());
    }

    @Test
    void sessionInProgress_isRejectedAndNothingChanges() throws Exception {
        String email = uniqueEmail();
        MvcResult signedUp = signedUp(email);
        given(activeSessionLookup.find(anyLong()))
                .willReturn(Optional.of(new ActiveSessionSummary(1, 1, "룸", "채린", Instant.now())));

        mvc.perform(delete("/api/me").header("Authorization", bearer(signedUp)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_IN_PROGRESS"));

        assertThat(jdbc.queryForObject("SELECT withdrawn_at IS NULL FROM users WHERE email = ?", Boolean.class, email))
                .isTrue();
        mvc.perform(post("/api/auth/refresh").cookie(refreshCookie(signedUp))).andExpect(status().isOk());
    }

    @Test
    void withoutToken_isUnauthorized() throws Exception {
        mvc.perform(delete("/api/me")).andExpect(status().isUnauthorized());
    }

    private int count(String table, long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
    }

    private static String kakaoBody(String code) {
        return """
                {"code":"%s","redirectUri":"http://localhost/oauth/kakao"}
                """.formatted(code);
    }

    private static Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("refresh_token");
        assertThat(cookie).isNotNull();
        return new Cookie(cookie.getName(), cookie.getValue());
    }
}
