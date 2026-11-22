package com.capsule.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.user.api.ReferralRewards;
import com.capsule.user.api.UserTypes.ReferralReward;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** 친구 초대 (api.md 35 · 36 '초대 규칙', 22 '서버가 하는 일' 7) */
class ReferralApiTest extends UserApiTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReferralRewards rewards;

    @Test
    void enterCodeThenFirstConnectionRewardsBoth() throws Exception {
        long inviter = user("성욱", "dev-inviter");
        long invitee = user("민지", "dev-invitee");
        String code = codeOf(inviter);

        enter(invitee, code.toLowerCase(), "dev-new") // 대소문자 안 가림
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.inviterNickname").value("성욱"))
                .andExpect(jsonPath("$.data.rewardOnFirstConnection").value(1000));
        expectError(enter(invitee, code, "dev-new"), 409, "REFERRAL_ALREADY_ENTERED");

        mvc.perform(get("/api/referrals/me").with(login(inviter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(code))
                .andExpect(jsonPath("$.data.link").value("https://capsule.ai.kr/i/" + code))
                .andExpect(jsonPath("$.data.rewardedCount").value(0))
                .andExpect(jsonPath("$.data.maxRewards").value(10))
                .andExpect(jsonPath("$.data.friends[0].nickname").value("민지"))
                .andExpect(jsonPath("$.data.friends[0].status").value("JOINED"))
                .andExpect(jsonPath("$.data.friends[0].rewarded").value(false))
                .andExpect(jsonPath("$.data.friends[0].joinedAt").isString())
                .andExpect(jsonPath("$.data.canEnterCode").value(true))
                .andExpect(jsonPath("$.data.codeInputDeadline").isString());
        mvc.perform(get("/api/referrals/me").with(login(invitee)))
                .andExpect(jsonPath("$.data.canEnterCode").value(false))
                .andExpect(jsonPath("$.data.codeInputDeadline").value(Matchers.nullValue()));

        assertThat(rewards.qualify(invitee, 501L, Instant.now())).contains(new ReferralReward(1000));
        assertThat(rewards.qualify(invitee, 502L, Instant.now())).isEmpty(); // 한 번만
        assertThat(rewards.rewardOf(invitee, 501L)).contains(new ReferralReward(1000));
        assertThat(rewards.rewardOf(invitee, 502L)).isEmpty();
        assertThat(inviteCredits(inviter)).isEqualTo(1000);
        assertThat(inviteCredits(invitee)).isEqualTo(1000);

        mvc.perform(get("/api/referrals/me").with(login(inviter)))
                .andExpect(jsonPath("$.data.rewardedCount").value(1))
                .andExpect(jsonPath("$.data.rewardedCredits").value(1000))
                .andExpect(jsonPath("$.data.friends[0].status").value("QUALIFIED"))
                .andExpect(jsonPath("$.data.friends[0].rewarded").value(true));
        mvc.perform(get("/api/credits").with(login(inviter)))
                .andExpect(jsonPath("$.data.invitedFriends").value(1));
        mvc.perform(get("/api/credits").with(login(invitee)))
                .andExpect(jsonPath("$.data.invitedFriends").value(0)); // 받은 사람 몫은 친구 초대가 아님
    }

    @Test
    void rejectsInvalidCodes() throws Exception {
        long inviter = user("성욱", "dev-a");
        long me = user("민지", "dev-b");
        String code = codeOf(inviter);

        expectError(enter(me, "", "dev-c"), 400, "INVALID_REQUEST");
        expectError(enter(me, code, ""), 400, "INVALID_REQUEST");
        expectError(enter(me, "CAP-NONE", "dev-c"), 404, "REFERRAL_CODE_NOT_FOUND");
        expectError(enter(me, codeOf(me), "dev-c"), 400, "REFERRAL_SELF");
        expectError(enter(me, code, "dev-a"), 400, "REFERRAL_SAME_DEVICE"); // 입력한 기기가 초대한 사람 가입 기기

        long sameSignupDevice = user("준호", "dev-a"); // 가입 기기가 같음
        expectError(enter(sameSignupDevice, code, "dev-z"), 400, "REFERRAL_SAME_DEVICE");

        long old = user("지난", "dev-old");
        jdbc.update("UPDATE users SET created_at = now() - interval '7 days' WHERE id = ?", old);
        expectError(enter(old, code, "dev-old2"), 400, "REFERRAL_EXPIRED");
        mvc.perform(get("/api/referrals/me").with(login(old)))
                .andExpect(jsonPath("$.data.canEnterCode").value(false));

        jdbc.update("UPDATE users SET withdrawn_at = now() WHERE id = ?", inviter);
        expectError(enter(me, code, "dev-c"), 404, "REFERRAL_CODE_NOT_FOUND"); // 탈퇴한 사람의 코드
        mvc.perform(get("/api/referrals/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void inviterRewardStopsAtTen() throws Exception {
        long inviter = user("성욱", "dev-top");
        for (int i = 0; i < 11; i++) {
            long friend = user("친구" + i, "dev-f" + i);
            enter(friend, codeOf(inviter), "dev-g" + i).andExpect(status().isCreated());
            assertThat(rewards.qualify(friend, 600L + i, Instant.now())).isPresent(); // 받은 사람 몫은 11번째도
        }
        assertThat(inviteCredits(inviter)).isEqualTo(10_000);
        mvc.perform(get("/api/referrals/me").with(login(inviter)))
                .andExpect(jsonPath("$.data.rewardedCount").value(10))
                .andExpect(jsonPath("$.data.friends.length()").value(11))
                .andExpect(jsonPath("$.data.friends[0].rewarded").value(false)); // 최근 순: 11번째
    }

    @Test
    void withdrawnFriendHasNoNickname() throws Exception {
        long inviter = user("성욱", "dev-w1");
        long friend = user("민지", "dev-w2");
        enter(friend, codeOf(inviter), "dev-w3").andExpect(status().isCreated());
        jdbc.update("UPDATE users SET withdrawn_at = now(), nickname = '탈퇴한 사용자' WHERE id = ?", friend);
        mvc.perform(get("/api/referrals/me").with(login(inviter)))
                .andExpect(jsonPath("$.data.friends[0].nickname").value(Matchers.nullValue()));
        assertThat(rewards.qualify(inviter, 1L, Instant.now())).isEqualTo(Optional.empty()); // 입력 안 한 사람
    }

    // ---- 도우미

    long user(String nickname, String signupDevice) {
        String suffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        long id = jdbc.queryForObject(
                "INSERT INTO users (nickname, referral_code) VALUES (?, ?) RETURNING id",
                Long.class,
                nickname,
                "T" + suffix + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        jdbc.update(
                "INSERT INTO signup_devices (device_hash, user_id) VALUES (?, ?)", signupDevice + "-" + testRun, id);
        return id;
    }

    /** 기기 값이 다른 테스트와 겹치지 않게 (같은 DB를 쓴다) */
    final String testRun = UUID.randomUUID().toString().substring(0, 8);

    String codeOf(long userId) {
        return jdbc.queryForObject("SELECT referral_code FROM users WHERE id = ?", String.class, userId);
    }

    int inviteCredits(long userId) {
        return jdbc.queryForObject(
                "SELECT COALESCE(sum(amount), 0) FROM credit_grants WHERE user_id = ? AND kind = 'INVITE'",
                Integer.class,
                userId);
    }

    ResultActions enter(long userId, String code, String device) throws Exception {
        String deviceHash = device.isEmpty() ? "" : device + "-" + testRun;
        return mvc.perform(post("/api/referrals")
                .with(login(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\",\"deviceHash\":\"%s\"}".formatted(code, deviceHash)));
    }

    static RequestPostProcessor login(long userId) {
        return jwt().jwt(j -> j.subject(String.valueOf(userId)));
    }

    static void expectError(ResultActions result, int httpStatus, String code) throws Exception {
        result.andExpect(status().is(httpStatus)).andExpect(jsonPath("$.code").value(code));
    }
}
