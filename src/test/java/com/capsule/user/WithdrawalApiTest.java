package com.capsule.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
        long userId = ((Number) read(signedUp, "$.data.userId")).longValue();
        Cookie refresh = refreshCookie(signedUp);

        jdbc.update(
                "INSERT INTO notifications (user_id, type, title, dedup_key) VALUES (?, 'ATTENDANCE', '오늘 출석 크레딧 1,500', 'day:t')",
                userId);
        // 일정 저장 API(13)는 지웠다: 남아 있던 일정도 탈퇴 때 지워지는지 (행을 직접 넣음)
        jdbc.update(
                "INSERT INTO user_memories (user_id, type, title, due_date) VALUES (?, 'EXAM', '중간고사', '2026-12-01')",
                userId);
        long alertMate = jdbc.queryForObject(
                "INSERT INTO characters (name, coming_soon, is_active, sort_order) VALUES ('탈퇴 알림 테스트', true, true, 9000) RETURNING id",
                Long.class);
        mvc.perform(post("/api/mates/{id}/alert", alertMate).header("Authorization", auth))
                .andExpect(status().isNoContent());
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
                "INSERT INTO conversation_recaps (conversation_id, connection_id, content) VALUES (?, 1, '카드')",
                conversationId);

        mvc.perform(delete("/api/me").header("Authorization", auth))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));

        // 행은 남고 개인정보만 비움
        var row = jdbc.queryForMap(
                "SELECT nickname, email, password, avatar_id, withdrawn_at, birth_date, honorific, about_me"
                        + " FROM users WHERE id = ?",
                userId);
        assertThat(row.get("nickname")).isEqualTo("탈퇴한 사용자");
        assertThat(row.get("email")).isNull();
        assertThat(row.get("password")).isNull();
        assertThat(row.get("withdrawn_at")).isNotNull();
        // 생년월일 · 호칭 · 나에 대해도 개인정보라 비움. 가입 기기는 초대 부정 방지용으로 남김
        assertThat(row.get("birth_date")).isNull();
        assertThat(row.get("honorific")).isNull();
        assertThat(row.get("about_me")).isNull();
        assertThat(count("signup_devices", userId)).isEqualTo(1);
        // 직접 말한 내용 · 대화 기억 삭제, 동의 기록은 남김
        assertThat(count("user_memories", userId)).isZero();
        assertThat(count("mate_alerts", userId)).isZero(); // 메이트 공개 알림 신청도 지움 (46)
        assertThat(count("notifications", userId)).isZero(); // 알림도 지움 (43)
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
        mvc.perform(get("/api/me/settings").header("Authorization", auth)).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/me/settings")
                        .header("Authorization", auth)
                        .contentType("application/json")
                        .content("{\"voiceEnabled\":false}"))
                .andExpect(status().isUnauthorized());
        // 남은 액세스 토큰으로 메이트 공개 알림(46)을 다시 신청할 수 없다
        mvc.perform(post("/api/mates/{id}/alert", alertMate).header("Authorization", auth))
                .andExpect(status().isUnauthorized());
        assertThat(count("mate_alerts", userId)).isZero();
        mvc.perform(delete("/api/me").header("Authorization", auth)).andExpect(status().isNoContent());

        // 이메일이 비워져서 같은 이메일로 새로 가입할 수 있다 (인증 요청은 1분 제한이라 DB로 확인)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email))
                .isZero();
    }

    @Test
    void kakaoAccountCanSignUpAgainAsNewUser() throws Exception {
        String code = "fake-kakao:%s:카카오닉네임".formatted(UUID.randomUUID());
        MvcResult first = kakaoSignedUp(code);

        mvc.perform(delete("/api/me").header("Authorization", bearer(first))).andExpect(status().isNoContent());

        // 탈퇴하면 연결이 끊겨 다시 새 사용자 (6 → 49)
        MvcResult second = kakaoSignedUp(code);
        assertThat(((Number) read(second, "$.data.userId")).longValue())
                .isNotEqualTo(((Number) read(first, "$.data.userId")).longValue());
    }

    @Test
    void sessionInProgress_isRejectedAndNothingChanges() throws Exception {
        String email = uniqueEmail();
        MvcResult signedUp = signedUp(email);
        given(activeSessionLookup.find(anyLong()))
                .willReturn(Optional.of(new ActiveSessionSummary(1, 1, "채린", Instant.now())));

        mvc.perform(delete("/api/me").header("Authorization", bearer(signedUp)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONNECTION_IN_PROGRESS"));

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

    private static Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("refresh_token");
        assertThat(cookie).isNotNull();
        return new Cookie(cookie.getName(), cookie.getValue());
    }
}
