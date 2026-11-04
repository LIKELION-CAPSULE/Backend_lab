package com.capsule.dialogue.repository;

import com.capsule.dialogue.client.LlmJson;
import com.capsule.dialogue.config.DialogueProperties.ScoreRule;
import com.capsule.dialogue.dto.CharacterContent;
import com.capsule.dialogue.dto.CharacterContent.Event;
import com.capsule.dialogue.dto.CharacterContent.Example;
import com.capsule.dialogue.dto.CharacterContent.Line;
import com.capsule.dialogue.dto.CharacterContent.Lore;
import com.capsule.dialogue.dto.CharacterContent.Move;
import com.capsule.dialogue.dto.CharacterContent.Sample;
import com.capsule.dialogue.dto.CharacterContent.World;
import com.capsule.relation.api.RelationTypes.SignalType;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

/**
 * 캐릭터 설정 전체를 한 번에 읽고 바꾼다 (테이블 8개를 문서 하나처럼). 바꾸기는 그 캐릭터의 행을 지우고 다시 넣는다.
 * text[]는 jsonb 배열로 넘겨 바꾼다(드라이버 배열 타입을 쓰지 않으려고).
 */
@Repository
public class CharacterContentRepository {

    private static final TypeReference<List<Example>> EXAMPLES = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};
    private static final TypeReference<List<Sample>> SAMPLES = new TypeReference<>() {};

    private final JdbcClient jdbc;

    CharacterContentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    /** 이 캐릭터에 설정이 있는지 (어느 폴더에서 왔든) */
    public boolean existsCharacter(long characterId) {
        return jdbc.sql("SELECT count(*) FROM character_profiles WHERE character_id = ?")
                        .param(characterId)
                        .query(Integer.class)
                        .single()
                > 0;
    }

    /** 이 콘텐츠 폴더(code)의 설정이 이미 들어갔는지 */
    public boolean existsCode(String code) {
        return jdbc.sql("SELECT count(*) FROM character_profiles WHERE code = ?")
                        .param(code)
                        .query(Integer.class)
                        .single()
                > 0;
    }

    @Transactional(readOnly = true)
    public Optional<CharacterContent> find(long characterId) {
        record Row(
                long worldId,
                String code,
                String speaker,
                String bible,
                String rules,
                String safety,
                List<String> expressions,
                String fallbacks,
                String tones,
                String studyStyle,
                String breakStyle,
                String samples) {}
        Optional<Row> profile = jdbc.sql("""
                        SELECT world_id, code, speaker, bible, rules, safety, expressions,
                               expression_fallbacks::text AS fallbacks, stage_tones::text AS tones, study_style, break_style,
                               samples::text AS samples
                        FROM character_profiles WHERE character_id = ?""")
                .param(characterId)
                .query((rs, n) -> new Row(
                        rs.getLong("world_id"),
                        rs.getString("code"),
                        rs.getString("speaker"),
                        rs.getString("bible"),
                        rs.getString("rules"),
                        rs.getString("safety"),
                        strings(rs, "expressions"),
                        rs.getString("fallbacks"),
                        rs.getString("tones"),
                        rs.getString("study_style"),
                        rs.getString("break_style"),
                        rs.getString("samples")))
                .optional();
        if (profile.isEmpty()) return Optional.empty();
        Row p = profile.get();

        World world = jdbc.sql("SELECT code, name, setting FROM worlds WHERE id = ?")
                .param(p.worldId())
                .query((rs, n) -> new World(
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("setting"),
                        lore(p.worldId()),
                        events(p.worldId())))
                .single();
        List<Move> moves = jdbc.sql("""
                        SELECT code, name, how, signals, modes, examples::text AS examples
                        FROM character_moves WHERE character_id = ? ORDER BY id""")
                .param(characterId)
                .query((rs, n) -> new Move(
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("how"),
                        strings(rs, "signals").stream().map(SignalType::valueOf).toList(),
                        strings(rs, "modes"),
                        LlmJson.JSON.readValue(rs.getString("examples"), EXAMPLES)))
                .list();
        List<Line> lines = jdbc.sql("""
                        SELECT situation, min_stage, sort, text, expression
                        FROM character_lines WHERE character_id = ? ORDER BY situation, sort, id""")
                .param(characterId)
                .query((rs, n) -> new Line(
                        rs.getString("situation"),
                        rs.getInt("min_stage"),
                        rs.getInt("sort"),
                        rs.getString("text"),
                        rs.getString("expression")))
                .list();
        Map<SignalType, ScoreRule> scores = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT signal, points, loss_percent, once_per_session, repeat_points
                        FROM character_scores WHERE character_id = ?""").param(characterId).query(rs -> {
            scores.put(
                    SignalType.valueOf(rs.getString("signal")),
                    new ScoreRule(
                            rs.getInt("points"),
                            rs.getInt("loss_percent"),
                            rs.getBoolean("once_per_session"),
                            rs.getObject("repeat_points", Integer.class)));
        });
        Map<String, String> habits = new LinkedHashMap<>();
        jdbc.sql("SELECT phrase, rule FROM character_habits WHERE character_id = ? ORDER BY id")
                .param(characterId)
                .query(rs -> {
                    habits.put(rs.getString("phrase"), rs.getString("rule"));
                });
        Map<Integer, String> tones = new LinkedHashMap<>();
        LlmJson.JSON.readValue(p.tones(), STRING_MAP).forEach((k, v) -> tones.put(Integer.parseInt(k), v));
        return Optional.of(new CharacterContent(
                characterId,
                p.code(),
                p.speaker(),
                p.bible(),
                p.rules(),
                p.safety(),
                p.expressions(),
                LlmJson.JSON.readValue(p.fallbacks(), STRING_MAP),
                tones,
                p.studyStyle(),
                p.breakStyle(),
                world,
                moves,
                lines,
                LlmJson.JSON.readValue(p.samples(), SAMPLES),
                scores,
                habits));
    }

    /** 이 캐릭터의 설정을 통째로 바꾼다 (실험실 저장). 세계(code 기준)는 있으면 고치고 지식 · 사건은 다시 넣는다 (같은 세계의 다른 캐릭터도 같이 바뀜) */
    @Transactional
    public void replace(CharacterContent c) {
        replace(c, true);
    }

    /** writeWorld가 false면 같은 code의 세계가 이미 있을 때 그대로 쓴다 (세계 지식 · 사건을 덮지 않음). 없으면 새로 넣는다 */
    @Transactional
    public void replace(CharacterContent c, boolean writeWorld) {
        Optional<Long> kept = writeWorld
                ? Optional.empty()
                : jdbc.sql("SELECT id FROM worlds WHERE code = ?")
                        .param(c.world().code())
                        .query(Long.class)
                        .optional();
        long worldId = kept.isPresent() ? kept.get() : writeWorld(c.world());
        replaceCharacter(c, worldId);
    }

    private long writeWorld(World w) {
        long worldId = jdbc.sql("""
                        INSERT INTO worlds (code, name, setting) VALUES (?, ?, ?)
                        ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, setting = EXCLUDED.setting, updated_at = now()
                        RETURNING id""")
                .params(w.code(), w.name(), w.setting())
                .query(Long.class)
                .single();
        jdbc.sql("DELETE FROM world_lore WHERE world_id = ?").param(worldId).update();
        for (Lore l : w.lore()) {
            jdbc.sql("""
                            INSERT INTO world_lore (world_id, code, title, keywords, body)
                            VALUES (?, ?, ?, ARRAY(SELECT jsonb_array_elements_text(CAST(? AS jsonb))), ?)""")
                    .params(worldId, l.code(), l.title(), LlmJson.write(l.keywords()), l.body())
                    .update();
        }
        jdbc.sql("DELETE FROM world_events WHERE world_id = ?").param(worldId).update();
        for (Event e : w.events()) {
            jdbc.sql("""
                            INSERT INTO world_events (world_id, code, text, hook, lore_code, weight)
                            VALUES (?, ?, ?, ?, ?, ?)""")
                    .params(worldId, e.code(), e.text(), e.hook(), e.loreCode(), Math.max(1, e.weight()))
                    .update();
        }
        return worldId;
    }

    private void replaceCharacter(CharacterContent c, long worldId) {
        long id = c.characterId();
        // 같은 폴더(code)가 다른 캐릭터에 붙어 있었으면 그것도 지운다 (reseed가 다른 캐릭터로 옮길 때 code UNIQUE 위반 방지)
        jdbc.sql("DELETE FROM character_profiles WHERE character_id = ? OR code = ?")
                .params(id, c.code())
                .update(); // 하위 행은 CASCADE
        jdbc.sql("""
                        INSERT INTO character_profiles
                            (character_id, world_id, code, speaker, bible, rules, safety, expressions,
                             expression_fallbacks, stage_tones, study_style, break_style, samples)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ARRAY(SELECT jsonb_array_elements_text(CAST(? AS jsonb))),
                                CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, CAST(? AS jsonb))""")
                .params(
                        id,
                        worldId,
                        c.code(),
                        c.speaker(),
                        c.bible(),
                        c.rules(),
                        c.safety(),
                        LlmJson.write(c.expressions()),
                        LlmJson.write(c.expressionFallbacks()),
                        LlmJson.write(c.stageTones()),
                        c.studyStyle(),
                        c.breakStyle(),
                        LlmJson.write(c.samples() == null ? List.of() : c.samples()))
                .update();
        for (Move m : c.moves()) {
            jdbc.sql("""
                            INSERT INTO character_moves (character_id, code, name, how, signals, modes, examples)
                            VALUES (?, ?, ?, ?, ARRAY(SELECT jsonb_array_elements_text(CAST(? AS jsonb))),
                                    ARRAY(SELECT jsonb_array_elements_text(CAST(? AS jsonb))), CAST(? AS jsonb))""")
                    .params(
                            id,
                            m.code(),
                            m.name(),
                            m.how(),
                            LlmJson.write(m.signals()),
                            LlmJson.write(m.modes() == null ? List.of() : m.modes()),
                            LlmJson.write(m.examples()))
                    .update();
        }
        for (Line l : c.lines()) {
            jdbc.sql("""
                            INSERT INTO character_lines (character_id, situation, min_stage, sort, text, expression)
                            VALUES (?, ?, ?, ?, ?, ?)""")
                    .params(id, l.situation(), Math.max(1, l.minStage()), l.sort(), l.text(), l.expression())
                    .update();
        }
        c.scores()
                .forEach((signal, r) -> jdbc.sql("""
                        INSERT INTO character_scores
                            (character_id, signal, points, loss_percent, once_per_session, repeat_points)
                        VALUES (?, ?, ?, ?, ?, ?)""")
                        .params(id, signal.name(), r.points(), r.lossPercent(), r.oncePerSession(), r.repeatPoints())
                        .update());
        c.habits()
                .forEach((phrase, rule) -> jdbc.sql(
                                "INSERT INTO character_habits (character_id, phrase, rule) VALUES (?, ?, ?)")
                        .params(id, phrase, rule)
                        .update());
    }

    private List<Lore> lore(long worldId) {
        return jdbc.sql("SELECT code, title, keywords, body FROM world_lore WHERE world_id = ? ORDER BY id")
                .param(worldId)
                .query((rs, n) -> new Lore(
                        rs.getString("code"), rs.getString("title"), strings(rs, "keywords"), rs.getString("body")))
                .list();
    }

    private List<Event> events(long worldId) {
        return jdbc.sql("SELECT code, text, hook, lore_code, weight FROM world_events WHERE world_id = ? ORDER BY id")
                .param(worldId)
                .query((rs, n) -> new Event(
                        rs.getString("code"),
                        rs.getString("text"),
                        rs.getString("hook"),
                        rs.getString("lore_code"),
                        rs.getInt("weight")))
                .list();
    }

    private static List<String> strings(ResultSet rs, String column) throws SQLException {
        Array a = rs.getArray(column);
        return a == null ? List.of() : Arrays.asList((String[]) a.getArray());
    }
}
