package com.capsule.dialogue.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 프록시 뒤에서는 접속 주소를 꾸밀 수 있어서, 토큰 없이 실험실(운영 설정 포함)을 켜면 시작하지 않는다 */
class LabSecurityConfigTest {

    @Test
    void behindProxyWithoutTokenRefusesToStart() {
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

        assertThatThrownBy(() -> new LabSecurityConfig().llmLabSecurity(null, noToken, "framework"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CAPSULE_LLM_LAB_TOKEN");
    }
}
