package com.capsule.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.capsule.TestcontainersConfiguration;
import com.capsule.session.api.ConnectionBackgrounds;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 연결의 지금 배경 (ADR 0032): 답장 때 바꾸고, 끝난 연결은 바꾸지 않는다 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ConnectionBackgroundsTest {

    @Autowired
    ConnectionBackgrounds backgrounds;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void changesOnlyInProgressConnections() {
        long userId = jdbc.queryForObject(
                "INSERT INTO users (nickname, email, password) VALUES ('테스트', ?, 'x') RETURNING id",
                Long.class,
                UUID.randomUUID() + "@background.test");
        long open = jdbc.queryForObject(
                "INSERT INTO connections (user_id, character_id) VALUES (?, 1) RETURNING id", Long.class, userId);
        assertThat(backgrounds.current(open)).isNull();

        assertThat(backgrounds.change(open, "cafe")).isTrue();
        assertThat(backgrounds.current(open)).isEqualTo("cafe");

        jdbc.update("UPDATE connections SET ended_at = now(), end_type = 'NORMAL' WHERE id = ?", open);
        assertThat(backgrounds.change(open, "classroom")).isFalse();
        assertThat(backgrounds.current(open)).isEqualTo("cafe");
        assertThat(backgrounds.current(999_999_999L)).isNull();
    }
}
