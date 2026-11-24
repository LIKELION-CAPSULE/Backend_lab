package com.capsule.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** 출시 알림 신청(api.md 45, 로그인 없이) · 준비 중 메이트 공개 알림 신청(46) */
class AlertRequestApiTest extends UserApiTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void waitlistTakesAnEmailWithoutLoginOnce() throws Exception {
        String ip = "10.0." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
        String email = uniqueEmail();
        waitlist(ip, "{\"email\":\" " + email.toUpperCase() + " \"}").andExpect(status().isNoContent());
        waitlist(ip, "{\"email\":\"" + email + "\"}").andExpect(status().isNoContent()); // 다시 신청해도 204
        assertThat(jdbc.queryForObject("SELECT count(*) FROM beta_waitlist WHERE email = ?", Integer.class, email))
                .isEqualTo(1);

        waitlist(ip, "{\"email\":\"not-an-email\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL"));
        // 같은 IP 1분 5번까지 (위에서 3번 씀)
        waitlist(ip, "{\"email\":\"" + uniqueEmail() + "\"}").andExpect(status().isNoContent());
        waitlist(ip, "{\"email\":\"" + uniqueEmail() + "\"}").andExpect(status().isNoContent());
        waitlist(ip, "{\"email\":\"" + uniqueEmail() + "\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void mateAlertOnlyForComingSoonMates() throws Exception {
        MvcResult user = signedUp(uniqueEmail());
        String auth = bearer(user);
        long userId = ((Number) read(user, "$.data.userId")).longValue();
        long soon = mate(true);
        long open = jdbc.queryForObject(
                "SELECT id FROM characters WHERE coming_soon = false AND is_active LIMIT 1", Long.class);

        mateAlert(auth, soon).andExpect(status().isNoContent());
        mateAlert(auth, soon).andExpect(status().isNoContent()); // 다시 눌러도 204, 한 줄
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM mate_alerts WHERE user_id = ? AND character_id = ?",
                        Integer.class,
                        userId,
                        soon))
                .isEqualTo(1);
        mateAlert(auth, open)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATE_ALREADY_OPEN"));
        mateAlert(auth, 999_999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATE_NOT_FOUND"));
        mvc.perform(post("/api/mates/{id}/alert", soon)).andExpect(status().isUnauthorized());
    }

    private ResultActions waitlist(String ip, String body) throws Exception {
        return mvc.perform(post("/api/beta/waitlist")
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions mateAlert(String auth, long mateId) throws Exception {
        return mvc.perform(post("/api/mates/{id}/alert", mateId).header(HttpHeaders.AUTHORIZATION, auth));
    }

    /** 테스트 전용 메이트 (모델 없는 준비 중 메이트는 schema CHECK상 가능) */
    private long mate(boolean comingSoon) {
        return jdbc.queryForObject(
                "INSERT INTO characters (name, coming_soon, is_active) VALUES (?, ?, true) RETURNING id",
                Long.class,
                "알림 테스트 " + UUID.randomUUID().toString().substring(0, 6),
                comingSoon);
    }
}
