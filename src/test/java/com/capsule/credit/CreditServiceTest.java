package com.capsule.credit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.TestcontainersConfiguration;
import com.capsule.credit.api.CreditCommands;
import com.capsule.credit.api.CreditQuery;
import com.capsule.credit.api.CreditTypes.Credits;
import com.capsule.credit.api.CreditTypes.SpendResult;
import com.capsule.credit.service.CreditService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 크레딧 규칙 (api.md 33, ADR 0031): 출석 1,500 · 다음 오전 5시 소멸, 초대분 소멸 없음, 출석분부터 사용, 요청 한 번만 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CreditServiceTest {

    @Autowired
    CreditQuery query;

    @Autowired
    CreditCommands commands;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    long userId;

    @BeforeEach
    void newUser() {
        userId = jdbc.queryForObject(
                "INSERT INTO users (nickname, email, password) VALUES ('테스트', ?, 'x') RETURNING id",
                Long.class,
                "credit-" + UUID.randomUUID() + "@test.invalid");
    }

    @Test
    void attendanceIsGrantedOncePerDay() {
        Credits first = query.current(userId);
        assertThat(first.todayRemaining()).isEqualTo(1500);
        assertThat(first.attendanceRemaining()).isEqualTo(1500);
        assertThat(first.inviteBalance()).isZero();
        assertThat(first.low()).isFalse();
        assertThat(first.attendanceExpiresAt()).isAfter(java.time.Instant.now());
        assertThat(query.current(userId)).isEqualTo(first); // 같은 날 다시 불러도 한 번만
        assertThat(types()).containsExactly("ATTENDANCE");
    }

    @Test
    void yesterdaysAttendanceExpiresAndTodaysIsGranted() {
        query.current(userId);
        commands.spend(userId, 20, "보이스 대화 · 채린", UUID.randomUUID());
        // 어제 받은 것으로 만든다 (오전 5시가 지남)
        jdbc.update(
                "UPDATE credit_grants SET granted_on = granted_on - 1, expires_at = now() - interval '1 second'"
                        + " WHERE user_id = ?",
                userId);
        Credits now = query.current(userId);
        assertThat(now.todayRemaining()).isEqualTo(1500);
        assertThat(types()).containsExactly("ATTENDANCE", "USE", "EXPIRE", "ATTENDANCE");
        assertThat(jdbc.queryForList(
                        "SELECT change FROM credit_ledger WHERE user_id = ? ORDER BY id", Integer.class, userId))
                .containsExactly(1500, -20, -1480, 1500);
        assertThat(jdbc.queryForList(
                        "SELECT balance_after FROM credit_ledger WHERE user_id = ? ORDER BY id", Integer.class, userId))
                .containsExactly(1500, 1480, 0, 1500);
    }

    @Test
    void spendUsesAttendanceFirstOnceAndNeverGoesNegative() {
        assertThat(commands.grantInvite(userId, 1000, 1L, true, "친구 초대")).isTrue();
        assertThat(commands.grantInvite(userId, 1000, 1L, true, "친구 초대")).isFalse(); // 한 번만

        UUID request = UUID.randomUUID();
        SpendResult spent = commands.spend(userId, 1600, "고급 대화 · 채린", request);
        assertThat(spent.spent()).isTrue();
        assertThat(spent.credits().attendanceRemaining()).isZero(); // 사라지는 출석분부터
        assertThat(spent.credits().inviteBalance()).isEqualTo(900);
        assertThat(commands.spend(userId, 1600, "고급 대화 · 채린", request).credits().todayRemaining())
                .isEqualTo(900); // 같은 요청은 한 번만

        SpendResult tooMuch = commands.spend(userId, 901, "고급 대화 · 채린", UUID.randomUUID());
        assertThat(tooMuch.spent()).isFalse();
        assertThat(tooMuch.credits().todayRemaining()).isEqualTo(900);

        commands.spend(userId, 850, "고급 대화 · 채린", UUID.randomUUID());
        Credits low = query.current(userId);
        assertThat(low.low()).isTrue();
        assertThat(low.exhausted()).isFalse();
        commands.spend(userId, 50, "보이스 대화 · 채린", UUID.randomUUID());
        assertThat(query.current(userId).exhausted()).isTrue();
    }

    @Test
    void summaryAndTransactionsApi() throws Exception {
        commands.grantInvite(userId, 1000, 2L, false, "친구 초대"); // 받은 사람 몫: 친구를 초대한 건 아님
        commands.spend(userId, 50, "보이스 · 고급 대화 · 채린", UUID.randomUUID());
        String sub = String.valueOf(userId);

        mvc.perform(get("/api/credits").with(jwt().jwt(j -> j.subject(sub))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayRemaining").value(2450))
                .andExpect(jsonPath("$.data.attendanceRemaining").value(1450))
                .andExpect(jsonPath("$.data.inviteBalance").value(1000))
                .andExpect(jsonPath("$.data.todayUsed").value(50))
                .andExpect(jsonPath("$.data.invitedFriends").value(0))
                .andExpect(jsonPath("$.data.attendanceExpiresAt").isString());

        // 최신순: 사용 → 초대 → 출석 (초대 지급 때 그날 출석이 먼저 들어감)
        String first = mvc.perform(
                        get("/api/credits/transactions").param("size", "2").with(jwt().jwt(j -> j.subject(sub))))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].type").value("USE"))
                .andExpect(jsonPath("$.data.items[0].change").value(-50))
                .andExpect(jsonPath("$.data.items[0].balanceAfter").value(2450))
                .andExpect(jsonPath("$.data.items[1].type").value("INVITE"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String cursor = com.jayway.jsonpath.JsonPath.read(first, "$.data.nextCursor");
        mvc.perform(get("/api/credits/transactions")
                        .param("size", "2")
                        .param("cursor", cursor)
                        .with(jwt().jwt(j -> j.subject(sub))))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("ATTENDANCE"))
                .andExpect(jsonPath("$.data.nextCursor").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/api/credits/transactions").param("filter", "USE").with(jwt().jwt(j -> j.subject(sub))))
                .andExpect(jsonPath("$.data.items.length()").value(1));
        mvc.perform(get("/api/credits/transactions").param("filter", "NOPE").with(jwt().jwt(j -> j.subject(sub))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/credits")).andExpect(status().isUnauthorized());
    }

    @Test
    void invitedFriendsCountsOnlyInviterRewards() throws Exception {
        commands.grantInvite(userId, 1000, 7L, false, "친구 초대");
        commands.grantInvite(userId, 1000, 8L, true, "친구 초대");
        commands.grantInvite(userId, 1000, 9L, true, "친구 초대");
        String sub = String.valueOf(userId);
        mvc.perform(get("/api/credits").with(jwt().jwt(j -> j.subject(sub))))
                .andExpect(jsonPath("$.data.inviteBalance").value(3000))
                .andExpect(jsonPath("$.data.invitedFriends").value(2));
    }

    /** 하루는 오전 5시(KST) = 20:00Z에 바뀐다 */
    @Test
    void dayChangesAtFiveAmKst() {
        assertThat(CreditService.dayOf(Instant.parse("2026-11-30T19:59:59Z"))).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(CreditService.dayOf(Instant.parse("2026-11-30T20:00:00Z"))).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(CreditService.nextDayStart(Instant.parse("2026-11-30T19:59:59Z")))
                .isEqualTo(Instant.parse("2026-11-30T20:00:00Z"));
        assertThat(CreditService.nextDayStart(Instant.parse("2026-11-30T20:00:00Z")))
                .isEqualTo(Instant.parse("2026-12-01T20:00:00Z"));
    }

    @Test
    void contractRejectsMissingInputs() {
        assertThatThrownBy(() -> commands.spend(userId, 20, "보이스", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> commands.spend(userId, 20, null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> commands.grantInvite(userId, 0, 1L, true, "친구 초대"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private List<String> types() {
        return jdbc.queryForList("SELECT type FROM credit_ledger WHERE user_id = ? ORDER BY id", String.class, userId);
    }
}
