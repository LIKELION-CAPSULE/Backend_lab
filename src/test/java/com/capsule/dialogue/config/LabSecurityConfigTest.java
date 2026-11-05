package com.capsule.dialogue.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capsule.common.security.AuthProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 실험실 보안 (ADR 0026 · 0027): 프록시 뒤 토큰 없이 시작 금지, 관리자 로그인 토큰 */
class LabSecurityConfigTest {

    static final AuthProperties AUTH = new AuthProperties("test-only-secret-0123456789abcdef0123", null, false, "Lax");
    static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");

    static LabAdmins admins(Map<String, String> users, Instant now) {
        return new LabAdmins(new AdminProperties(users, Duration.ofHours(12)), AUTH, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void behindProxyWithoutTokenOrAdminsRefusesToStart() {
        LlmProperties noToken = new LlmProperties(
                "fake",
                null,
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                0,
                Duration.ZERO,
                false,
                null,
                null,
                null,
                Map.of(),
                Map.of(),
                new LlmProperties.Lab(true, "", 1));

        assertThatThrownBy(
                        () -> new LabSecurityConfig().llmLabSecurity(null, noToken, admins(Map.of(), NOW), "framework"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CAPSULE_ADMIN_USERS_");
    }

    @Test
    void loginTokenIsSignedExpiresAndDiesWithThePassword() {
        LabAdmins admins = admins(Map.of("seunghee", "{noop}pw-1234"), NOW);

        assertThat(admins.login("seunghee", "틀림")).isEmpty();
        assertThat(admins.login("nobody", "pw-1234")).isEmpty();
        String token = admins.login("seunghee", "pw-1234").orElseThrow().token();
        assertThat(admins.verify(token)).contains("seunghee");

        // 서명을 바꾸거나 다른 아이디로 바꾸면 안 됨
        assertThat(admins.verify(token.substring(0, token.length() - 2) + "AA")).isEmpty();
        assertThat(admins.verify("c29tZW9uZQ" + token.substring(token.indexOf('.'))))
                .isEmpty();
        assertThat(admins.verify("아무거나")).isEmpty();
        // 12시간이 지나면 끝
        assertThat(admins(Map.of("seunghee", "{noop}pw-1234"), NOW.plus(Duration.ofHours(12)))
                        .verify(token))
                .isEmpty();
        // 비밀번호를 바꾸면 그 전 토큰은 바로 못 씀
        assertThat(admins(Map.of("seunghee", "{noop}new-pass"), NOW).verify(token))
                .isEmpty();
    }

    @Test
    void passwordWithoutEncoderIdFailsAtStartup() {
        assertThatThrownBy(() -> admins(Map.of("seunghee", "plain"), NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("{bcrypt}");
    }
}
