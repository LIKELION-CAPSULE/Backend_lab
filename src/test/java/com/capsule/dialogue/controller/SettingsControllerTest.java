package com.capsule.dialogue.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.capsule.common.error.ApiException;
import com.capsule.common.settings.RuntimeSettings;
import com.capsule.dialogue.config.LabAdmins;
import com.capsule.dialogue.config.LlmProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

/** 공용 개발 DB에 토큰 없이 붙은 노트북은 운영 설정을 저장하지 못한다 (그 행을 개발 서버가 읽음, ADR 0026) */
class SettingsControllerTest {

    static LlmProperties lab(String token) {
        return new LlmProperties(
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
                new LlmProperties.Lab(true, token, 1));
    }

    static SettingsController controller(String token, String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return new SettingsController(
                List.of(RuntimeSettings.fixed("yml")), env, lab(token), mock(LabAdmins.class)); // 관리자 계정 없음
    }

    @Test
    void sharedDevDbWithoutTokenIsReadOnly() {
        var empty = JsonMapper.builder().build().createObjectNode();
        assertThatThrownBy(() -> controller("", "local", "shared-db").save("fixed", empty, null))
                .isInstanceOf(ApiException.class)
                .hasMessage("SETTINGS_READ_ONLY");
        // 토큰이 있는 개발 서버 · 로컬 DB는 막지 않는다 (고정 설정이라 저장 단계에서 실패하는 것으로 확인)
        for (SettingsController open :
                List.of(controller("token", "local", "shared-db", "dev-server"), controller("", "local")))
            assertThatThrownBy(() -> open.save("fixed", empty, null)).isInstanceOf(IllegalStateException.class);
    }
}
