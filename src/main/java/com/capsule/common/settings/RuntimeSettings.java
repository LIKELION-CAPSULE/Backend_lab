package com.capsule.common.settings;

import com.capsule.common.error.ApiException;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 실행 중에 바꾸는 설정 한 묶음 (ADR 0026). application.yml로 받은 값이 기본값이고, DB에 저장한 칸만 그 위에 덮어쓴다.
 * - 바꿀 수 있는 칸은 paths(허용 목록)뿐이다. API 키 · 공급자 주소처럼 바꾸면 위험한 칸은 넣지 않는다 (목록 밖 칸은 화면에도 안 나감)
 * - 칸은 점 경로(예: anthropic.models)이고, 그 칸 값을 통째로 바꾼다 (안쪽을 합치지 않음)
 * - 읽기(current)는 메모리 사본이고 DB를 건드리지 않는다. 처음 값은 빈을 만들 때(트랜잭션 밖) 읽고, 그 뒤 RELOAD가 지나면
 *   가상 스레드 하나가 따로 다시 읽는다: 요청 스레드에서 읽으면 호출한 쪽 트랜잭션 안에서 SQL이 돌아, 실패할 때 그 트랜잭션이 깨진다
 * - 저장하면 이 인스턴스는 바로, 다른 인스턴스는 RELOAD 뒤 다음 읽기에 바뀐다
 * - DB를 못 읽으면(마이그레이션 전) 이전 값으로 돌고 RELOAD 뒤 다시 읽는다. 저장된 값이 안 맞으면(칸 형식이 바뀐 배포 뒤) yml 값으로 돌고 로그를 남긴다
 *
 * 테이블은 모듈마다 자기 것을 쓴다(다른 모듈 테이블을 읽지 않으려고). 모양: section text PK, overrides jsonb, updated_at timestamptz
 */
public final class RuntimeSettings<T> {

    private static final Logger log = LoggerFactory.getLogger(RuntimeSettings.class);
    static final long RELOAD_NANOS = 30_000_000_000L;
    public static final String INVALID = "INVALID_SETTING";

    private final String table;
    private final String section;
    private final T defaults;
    private final Class<T> type;
    private final List<String> paths;
    private final Consumer<T> check;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final ReentrantLock lock = new ReentrantLock(); // 저장과 다시 읽기의 순서. synchronized는 가상 스레드를 묶어 둔다
    private final AtomicBoolean reloading = new AtomicBoolean();
    private volatile Snapshot<T> snap;

    private record Snapshot<T>(T value, JsonNode overrides, Instant updatedAt, long loadedAt) {}

    /** 화면 한 줄: 칸, yml 값, 지금 쓰는 값, 덮어썼는지 */
    public record Field(String path, JsonNode defaultValue, JsonNode value, boolean overridden) {}

    public record View(String section, Instant updatedAt, List<Field> fields) {}

    private RuntimeSettings(
            JdbcTemplate jdbc,
            JsonMapper json,
            String table,
            String section,
            T defaults,
            Class<T> type,
            List<String> paths,
            Consumer<T> check) {
        this.jdbc = jdbc;
        this.json = json;
        this.table = table;
        this.section = section;
        this.defaults = defaults;
        this.type = type;
        this.paths = List.copyOf(paths);
        this.check = check;
        if (jdbc == null) snap = new Snapshot<>(defaults, null, null, Long.MAX_VALUE);
        else reload(); // 빈을 만들 때: 트랜잭션 밖, JdbcTemplate은 Flyway 뒤에 생긴다
    }

    /**
     * DB(table의 section 행)에 저장하는 설정. check는 덮어쓴 결과 전체를 보고 틀리면 require로 던진다.
     * yml 값도 같은 check를 거친다: 틀리면 앱이 시작하지 않는다 (설정 오타를 첫 요청이 아니라 배포 때 잡게)
     */
    public static <T> RuntimeSettings<T> stored(
            JdbcTemplate jdbc,
            JsonMapper json,
            String table,
            String section,
            T defaults,
            Class<T> type,
            List<String> paths,
            Consumer<T> check) {
        try {
            check.accept(defaults);
        } catch (ApiException e) {
            throw new IllegalStateException("application.yml의 " + section + " 설정이 틀림: " + e.detail(), e);
        }
        return new RuntimeSettings<>(jdbc, json, table, section, defaults, type, paths, check);
    }

    /** 바뀌지 않는 값 (Spring · DB 없이 도는 테스트) */
    @SuppressWarnings("unchecked")
    public static <T> RuntimeSettings<T> fixed(T value) {
        return new RuntimeSettings<>(null, null, null, "fixed", value, (Class<T>) value.getClass(), List.of(), v -> {});
    }

    /** 레코드의 필드 전부(skip 빼고). 비밀값이 없는 설정만 이렇게 연다 */
    public static List<String> allExcept(Class<? extends Record> type, String... skip) {
        Set<String> skipped = Set.of(skip);
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .filter(n -> !skipped.contains(n))
                .toList();
    }

    /** check 안에서: 틀리면 400 INVALID_SETTING {path, reason} */
    public static void require(boolean ok, String path, String reason) {
        if (!ok) throw invalid(path, reason);
    }

    public String section() {
        return section;
    }

    /** yml 값 (시작할 때만 읽는 칸 · 시작 검사용) */
    public T defaults() {
        return defaults;
    }

    /** 지금 쓰는 값. DB를 읽지 않는다: 오래됐으면 다시 읽기를 따로 시작하고 이번에는 지금 사본을 준다 */
    public T current() {
        Snapshot<T> s = snap;
        if (jdbc != null && System.nanoTime() - s.loadedAt() > RELOAD_NANOS && reloading.compareAndSet(false, true))
            Thread.ofVirtual().name("runtime-settings-" + section).start(() -> {
                try {
                    reload();
                } finally {
                    reloading.set(false);
                }
            });
        return s.value();
    }

    /** 테스트: 다음 current()가 다시 읽기를 시작하게 */
    void expire() {
        lock.lock();
        try {
            Snapshot<T> s = snap;
            snap = new Snapshot<>(s.value(), s.overrides(), s.updatedAt(), System.nanoTime() - RELOAD_NANOS - 1);
        } finally {
            lock.unlock();
        }
    }

    public View view() {
        current();
        Snapshot<T> s = snap;
        JsonNode base = json.valueToTree(defaults);
        JsonNode now = json.valueToTree(s.value());
        List<Field> fields = paths.stream()
                .map(p -> new Field(
                        p,
                        at(base, p),
                        at(now, p),
                        s.overrides() != null && s.overrides().has(p)))
                .toList();
        return new View(section, s.updatedAt(), fields);
    }

    /** 덮어쓸 칸 전체를 바꾼다 ({칸: 값}, 빠진 칸은 yml 값). yml 값과 같은 칸은 저장하지 않는다 */
    public View update(JsonNode body) {
        if (jdbc == null) throw new IllegalStateException("고정 설정은 저장할 수 없음");
        if (body == null || !body.isObject()) throw invalid(section, "{칸: 값} 객체여야 함");
        JsonNode base = json.valueToTree(defaults);
        ObjectNode overrides = json.createObjectNode();
        for (Map.Entry<String, JsonNode> e : body.properties()) {
            if (!paths.contains(e.getKey())) throw invalid(e.getKey(), "바꿀 수 없는 칸");
            if (!e.getValue().equals(at(base, e.getKey()))) overrides.set(e.getKey(), e.getValue());
        }
        T value = apply(overrides); // 형식 · 검사가 틀리면 여기서 던져 저장하지 않는다
        lock.lock();
        try {
            OffsetDateTime at = jdbc.queryForObject(
                    "INSERT INTO " + table + " (section, overrides, updated_at) VALUES (?, ?::jsonb, now())"
                            + " ON CONFLICT (section) DO UPDATE SET overrides = EXCLUDED.overrides, updated_at = now()"
                            + " RETURNING updated_at",
                    OffsetDateTime.class,
                    section,
                    json.writeValueAsString(overrides));
            snap = new Snapshot<>(value, overrides, at.toInstant(), System.nanoTime());
        } finally {
            lock.unlock();
        }
        log.info("운영 설정 {} 저장: {}", section, overrides);
        return view();
    }

    private void reload() {
        lock.lock();
        try {
            Snapshot<T> s = snap;
            if (s != null && System.nanoTime() - s.loadedAt() <= RELOAD_NANOS) return; // 방금 저장함
            JsonNode overrides;
            Instant updatedAt;
            try {
                List<Snapshot<T>> rows = jdbc.query(
                        "SELECT overrides::text, updated_at FROM " + table + " WHERE section = ?",
                        (rs, i) -> new Snapshot<>(
                                null,
                                json.readTree(rs.getString(1)),
                                rs.getObject(2, OffsetDateTime.class).toInstant(),
                                0),
                        section);
                overrides = rows.isEmpty()
                        ? json.createObjectNode()
                        : rows.getFirst().overrides();
                updatedAt = rows.isEmpty() ? null : rows.getFirst().updatedAt();
            } catch (RuntimeException e) {
                log.warn("운영 설정 {}를 못 읽음, 이전 값으로 돌고 다시 읽음: {}", section, e.toString());
                snap = s == null
                        ? new Snapshot<>(defaults, null, null, System.nanoTime())
                        : new Snapshot<>(s.value(), s.overrides(), s.updatedAt(), System.nanoTime());
                return;
            }
            T value;
            try {
                value = apply(overrides);
            } catch (RuntimeException e) {
                log.error(
                        "저장된 운영 설정 {}가 맞지 않아 yml 값으로 돎: {}",
                        section,
                        e instanceof ApiException a ? a.detail() : e.toString());
                value = defaults;
            }
            snap = new Snapshot<>(value, overrides, updatedAt, System.nanoTime());
        } finally {
            lock.unlock();
        }
    }

    private T apply(JsonNode overrides) {
        ObjectNode tree = json.valueToTree(defaults);
        for (Map.Entry<String, JsonNode> e : overrides.properties()) {
            if (paths.contains(e.getKey())) set(tree, e.getKey(), e.getValue());
            else log.warn("운영 설정 {}의 {}는 이제 바꿀 수 없는 칸이라 무시", section, e.getKey());
        }
        T value;
        try {
            value = json.treeToValue(tree, type);
        } catch (JacksonException e) {
            throw invalid(section, "형식이 맞지 않음: " + e.getOriginalMessage());
        }
        check.accept(value);
        return value;
    }

    private static void set(ObjectNode tree, String path, JsonNode value) {
        String[] parts = path.split("\\.");
        ObjectNode node = tree;
        for (int i = 0; i < parts.length - 1; i++) {
            if (!(node.get(parts[i]) instanceof ObjectNode child)) throw invalid(path, "이 서버 설정에 없는 칸");
            node = child;
        }
        node.set(parts[parts.length - 1], value);
    }

    private static JsonNode at(JsonNode tree, String path) {
        JsonNode node = tree;
        for (String part : path.split("\\.")) node = node.path(part);
        return node.isMissingNode() ? null : node;
    }

    private static ApiException invalid(String path, String reason) {
        return new ApiException(
                HttpStatus.BAD_REQUEST, INVALID, Map.<String, Object>of("path", path, "reason", reason));
    }
}
