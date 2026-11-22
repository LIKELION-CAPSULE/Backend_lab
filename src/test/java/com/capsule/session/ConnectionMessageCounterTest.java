package com.capsule.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.capsule.TestcontainersConfiguration;
import com.capsule.session.api.ConnectionMessageCounter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 답장 저장 때 대화 수 +1 (api.md 22 messageCount, 2026-11-23 6단계). 끝난 연결은 세지 않는다 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ConnectionMessageCounterTest {

    @Autowired
    ConnectionMessageCounter counter;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void countsOnlyInProgressConnections() {
        long userId = jdbc.queryForObject(
                "INSERT INTO users (nickname, email, password) VALUES ('테스트', ?, 'x') RETURNING id",
                Long.class,
                UUID.randomUUID() + "@counter.test");
        long open = jdbc.queryForObject(
                "INSERT INTO connections (user_id, character_id) VALUES (?, 1) RETURNING id", Long.class, userId);
        assertThat(counter.countReply(open)).isTrue();
        assertThat(counter.countReply(open)).isTrue();
        assertThat(messageCount(open)).isEqualTo(2);

        jdbc.update("UPDATE connections SET ended_at = now(), end_type = 'NORMAL' WHERE id = ?", open);
        assertThat(counter.countReply(open)).isFalse();
        assertThat(messageCount(open)).isEqualTo(2);
        assertThat(counter.countReply(999_999_999L)).isFalse();
    }

    private int messageCount(long id) {
        return jdbc.queryForObject("SELECT message_count FROM connections WHERE id = ?", Integer.class, id);
    }
}
