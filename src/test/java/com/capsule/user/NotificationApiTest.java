package com.capsule.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.user.api.NotificationCommands;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** 알림 43 · 44 (ADR 0035): 출석(credit 이벤트) · 단계 상승(session 계약) · 읽음 · 페이지 · 안 읽은 수(8) */
class NotificationApiTest extends UserApiTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    NotificationCommands commands;

    @Test
    void attendanceAndStageUpBecomeNotificationsOnce() throws Exception {
        long userId = user("승희");
        // 그날 처음 크레딧을 읽으면 출석 지급 → 같은 트랜잭션에서 알림. 다시 읽어도 한 번
        mvc.perform(get("/api/credits").with(login(userId))).andExpect(status().isOk());
        mvc.perform(get("/api/credits").with(login(userId))).andExpect(status().isOk());
        list(userId, null)
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("ATTENDANCE"))
                .andExpect(jsonPath("$.data.items[0].title").value("오늘 출석 크레딧 1,500"))
                .andExpect(jsonPath("$.data.items[0].link").value("/credits"))
                .andExpect(jsonPath("$.data.items[0].read").value(false))
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        // 단계 상승 (22에서 session이 부름): 조사는 이름 받침에 맞춘다. 같은 연결은 한 번만
        commands.stageUp(userId, 501L, "채린", 3, "친구", true);
        commands.stageUp(userId, 501L, "채린", 3, "친구", true);
        commands.stageUp(userId, 502L, "다희", 2, "아는 사이", false);
        commands.stageUp(userId, 503L, "서진", 4, null, false);
        list(userId, null)
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.items[0].title").value("서진과 4단계가 됐어요"))
                .andExpect(jsonPath("$.data.items[1].title").value("다희와 \"아는 사이\"가 됐어요"))
                .andExpect(jsonPath("$.data.items[2].title").value("채린과 \"친구\"가 됐어요 · 새 표정"))
                .andExpect(jsonPath("$.data.items[2].link").value("/mates/me"))
                .andExpect(jsonPath("$.data.unreadCount").value(4));
    }

    @Test
    void readMarksOnlyMineAndPagesGoBack() throws Exception {
        long userId = user("성욱");
        long other = user("남");
        for (long c = 1; c <= 3; c++) commands.stageUp(userId, 900 + c, "채린", 2, "아는 사이", false);
        commands.stageUp(other, 999L, "채린", 2, "아는 사이", false);
        long othersId = jdbc.queryForObject("SELECT id FROM notifications WHERE user_id = ?", Long.class, other);

        String first = list(userId, "size=2")
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.nextBefore").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long before = ((Number) JsonPath.read(first, "$.data.nextBefore")).longValue();
        long newest = ((Number) JsonPath.read(first, "$.data.items[0].id")).longValue();
        list(userId, "size=2&before=" + before)
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.nextBefore").value(Matchers.nullValue()));

        // 고른 것만 읽음, 남의 id는 무시
        read(userId, "{\"ids\":[" + newest + "," + othersId + "]}")
                .andExpect(jsonPath("$.data.unreadCount").value(2));
        assertThat(jdbc.queryForObject(
                        "SELECT read_at IS NULL FROM notifications WHERE id = ?", Boolean.class, othersId))
                .isTrue();
        // 내 정보(8)의 벨 점
        mvc.perform(get("/api/me").with(login(userId)))
                .andExpect(jsonPath("$.data.unreadNotifications").value(3)); // 출석 +1
        // ids 없이 = 전부
        read(userId, "{}").andExpect(jsonPath("$.data.unreadCount").value(0));
        list(userId, "size=0").andExpect(status().isBadRequest());
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void notificationsOlderThan90DaysAreLeftOut() throws Exception {
        long userId = user("오래");
        commands.stageUp(userId, 1L, "채린", 2, "아는 사이", false);
        jdbc.update("UPDATE notifications SET created_at = now() - interval '91 days' WHERE user_id = ?", userId);
        list(userId, null)
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    private ResultActions list(long userId, String query) throws Exception {
        return mvc.perform(get("/api/notifications" + (query == null ? "" : "?" + query))
                        .with(login(userId)))
                .andExpect(status().is(query != null && query.contains("size=0") ? 400 : 200));
    }

    private ResultActions read(long userId, String body) throws Exception {
        return mvc.perform(post("/api/notifications/read")
                .with(login(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long user(String nickname) {
        return jdbc.queryForObject(
                "INSERT INTO users (nickname, referral_code, avatar_id, birth_date) VALUES (?, ?, 1, '2001-04-10') RETURNING id",
                Long.class,
                nickname,
                "N" + UUID.randomUUID().toString().substring(0, 9).toUpperCase());
    }

    private static RequestPostProcessor login(long userId) {
        return jwt().jwt(j -> j.subject(String.valueOf(userId)));
    }
}
