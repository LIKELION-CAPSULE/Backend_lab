package com.capsule.common.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capsule.TestcontainersConfiguration;
import com.capsule.common.error.ApiException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

/** 운영 설정: yml 값 위에 저장한 칸만 덮어쓰고, 허용 목록 밖 · 틀린 값은 저장하지 않는다 (ADR 0026) */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RuntimeSettingsTest {

    record Sample(int count, Duration delay, Inner inner, String secret) {
        record Inner(String name, List<String> tags) {}
    }

    static final Sample YML = new Sample(2, Duration.ofSeconds(5), new Sample.Inner("말포이", List.of("a")), "key");
    static final List<String> PATHS = List.of("count", "delay", "inner.tags");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JsonMapper json;

    String section;

    @BeforeEach
    void table() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS test_settings (section text PRIMARY KEY,"
                + " overrides jsonb NOT NULL DEFAULT '{}', updated_at timestamptz NOT NULL DEFAULT now())");
        section = UUID.randomUUID().toString();
    }

    RuntimeSettings<Sample> settings() {
        return RuntimeSettings.stored(
                jdbc,
                json,
                "test_settings",
                section,
                YML,
                Sample.class,
                PATHS,
                v -> RuntimeSettings.require(v.count() > 0, "count", "1 이상"));
    }

    @Test
    void overridesOnlySavedPathsAndOtherInstancesReadThem() {
        RuntimeSettings<Sample> s = settings();
        assertThat(s.current()).isEqualTo(YML);

        RuntimeSettings.View view = s.update(json.readTree("{\"count\": 3, \"inner.tags\": [\"b\", \"c\"]}"));

        assertThat(s.current())
                .isEqualTo(new Sample(3, YML.delay(), new Sample.Inner("말포이", List.of("b", "c")), "key"));
        assertThat(view.fields())
                .extracting(RuntimeSettings.Field::path)
                .containsExactlyElementsOf(PATHS); // secret은 안 나감
        assertThat(view.fields()).extracting(RuntimeSettings.Field::overridden).containsExactly(true, false, true);
        assertThat(view.updatedAt()).isNotNull();
        // 새로 띄운 인스턴스(재시작 · 다른 서버)도 같은 값
        assertThat(settings().current()).isEqualTo(s.current());
    }

    @Test
    void ymlEqualValuesAreNotStored() {
        RuntimeSettings<Sample> s = settings();
        s.update(json.readTree("{\"count\": 2}"));

        assertThat(s.view().fields()).noneMatch(RuntimeSettings.Field::overridden);
        assertThat(jdbc.queryForObject(
                        "SELECT overrides::text FROM test_settings WHERE section = ?", String.class, section))
                .isEqualTo("{}");
    }

    @Test
    void rejectsLockedPathsWrongTypesAndFailedChecks() {
        RuntimeSettings<Sample> s = settings();

        for (String body : List.of("{\"secret\": \"x\"}", "{\"count\": \"많이\"}", "{\"count\": 0}", "[1]")) {
            assertThatThrownBy(() -> s.update(json.readTree(body)))
                    .as(body)
                    .isInstanceOfSatisfying(
                            ApiException.class, e -> assertThat(e.code()).isEqualTo(RuntimeSettings.INVALID));
        }
        assertThat(s.current()).isEqualTo(YML);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM test_settings WHERE section = ?", Integer.class, section))
                .isZero();
    }

    @Test
    void brokenStoredValueOrMissingTableFallsBackToYml() {
        jdbc.update("INSERT INTO test_settings (section, overrides) VALUES (?, '{\"count\": \"x\"}'::jsonb)", section);
        assertThat(settings().current()).isEqualTo(YML);

        RuntimeSettings<Sample> noTable =
                RuntimeSettings.stored(jdbc, json, "no_such_table", section, YML, Sample.class, PATHS, v -> {});
        assertThat(noTable.current()).isEqualTo(YML);
    }
}
