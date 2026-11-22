package com.capsule.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.user.api.BetaFeedbackQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/** 베타 피드백 (api.md 42 · 23 betaFeedbackAvailable) */
class FeedbackApiTest extends UserApiTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    BetaFeedbackQuery query;

    long userId;

    @BeforeEach
    void newUser() {
        userId = jdbc.queryForObject("INSERT INTO users (nickname) VALUES ('피드백') RETURNING id", Long.class);
    }

    @Test
    void ratingOncePerDayAndOpinionsAnyTime() throws Exception {
        assertThat(query.ratingAvailableToday(userId)).isTrue();
        send("{\"kind\":\"RATING\",\"score\":4,\"text\":\"반응이 귀여워요\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.feedbackId").isNumber());
        assertThat(query.ratingAvailableToday(userId)).isFalse();
        send("{\"kind\":\"RATING\",\"score\":3}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEEDBACK_ALREADY_TODAY"));
        send("{\"kind\":\"BUG\",\"text\":\"PIP가 안 떠요\"}").andExpect(status().isCreated());
        send("{\"kind\":\"SUGGESTION\",\"text\":\"다크 모드 말고 밝은 테마도\"}").andExpect(status().isCreated());
    }

    @Test
    void rejectsInvalidRequests() throws Exception {
        String[] bad = {
            "{\"kind\":\"NOPE\",\"text\":\"x\"}",
            "{\"kind\":\"RATING\"}",
            "{\"kind\":\"RATING\",\"score\":5}",
            "{\"kind\":\"RATING\",\"score\":0}",
            "{\"kind\":\"BUG\"}",
            "{\"kind\":\"BUG\",\"text\":\"  \"}",
            "{\"kind\":\"BUG\",\"score\":3,\"text\":\"x\"}",
            "{\"kind\":\"BUG\",\"text\":\"%s\"}".formatted("가".repeat(1001)),
            "{\"kind\":\"BUG\",\"text\":\"x\",\"connectionId\":999999999}" // 내 연결이 아님
        };
        for (String body : bad) {
            send(body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        mvc.perform(post("/api/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    ResultActions send(String body) throws Exception {
        return mvc.perform(post("/api/feedback")
                .with(jwt().jwt(j -> j.subject(String.valueOf(userId))))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
