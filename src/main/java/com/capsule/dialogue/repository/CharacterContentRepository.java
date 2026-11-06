package com.capsule.dialogue.repository;

import com.capsule.dialogue.client.LlmJson;
import com.capsule.dialogue.config.DialogueProperties.ScoreRule;
import com.capsule.dialogue.dto.CharacterContent;
import com.capsule.dialogue.dto.CharacterContent.Line;
import com.capsule.dialogue.dto.CharacterContent.Lore;
import com.capsule.dialogue.dto.CharacterContent.Sample;
import com.capsule.relation.api.RelationTypes.SignalType;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
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
 * 캐릭터 설정 전체를 한 번에 읽고 바꾼다 (테이블 4개를 문서 하나처럼). 바꾸기는 그 캐릭터의 행을 지우고 다시 넣는다.
 * text[]는 jsonb 배열로 넘겨 바꾼다(드라이버 배열 타입을 쓰지 않으려고).
 */
@Repository
public class CharacterContentRepository {

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};
    private static final TypeReference<List<Sample>> SAMPLES = new TypeReference<>() {};
    private static final TypeReference<List<Lore>> LORE = new TypeReference<>() {};

    private final JdbcClient jdbc;

    CharacterContentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    /** 이 캐릭터에 붙은 설정의 폴더(code). 없으면 빈 값 */
    public Optional<String> codeOf(long characterId) {
        return jdbc.sql("SELECT code FROM character_profiles WHERE character_id = ?")
                .param(characterId)
                .query(String.class)
                .optional();
    }

    /** 이 콘텐츠 폴더(code)에서 불러온 설정의 버전. 아직 안 들어갔으면 빈 값 */
    public Optional<Integer> versionOf(String code) {
        return jdbc.sql("SELECT content_version FROM character_profiles WHERE code = ?")
                .param(code)
                .query(Integer.class)
                .optional();
    }

    /** 실험실에서 마지막으로 고친 때. 파일 값 그대로면 빈 값 */
    public Optional<Instant> labEditedAt(String code) {
        return jdbc.sql("SELECT lab_edited_at FROM character_profiles WHERE code = ? AND lab_edited_at IS NOT NULL")
                .param(code)
                .query((rs, n) -> rs.getTimestamp(1).toInstant())
                .optional();
    }

    @Transactional(readOnly = true)
    public Optional<CharacterContent> find(long characterId) {
        record Row(
                String code,
                int version,
                String speaker,
                String bible,
                String rules,
                String safety,
                List<String> expressions,
                String guide,
                String tones,
                String studyStyle,
                String breakStyle,
                String samples,
                String lore) {}
        Optional<Row> profile = jdbc.sql("""
                        SELECT code, content_version, speaker, bible, rules, safety, expressions,
                               expression_guide::text AS guide, stage_tones::text AS tones, study_style, break_style,
                               samples::text AS samples, lore::text AS lore
                        FROM character_profiles WHERE character_id = ?""")
                .param(characterId)
                .query((rs, n) -> new Row(
                        rs.getString("code"),
                        rs.getInt("content_version"),
                        rs.getString("speaker"),
                        rs.getString("bible"),
                        rs.getString("rules"),
                        rs.getString("safety"),
                        strings(rs, "expressions"),
                        rs.getString("guide"),
                        rs.getString("tones"),
                        rs.getString("study_style"),
                        rs.getString("break_style"),
                        rs.getString("samples"),
                        rs.getString("lore")))
                .optional();
        if (profile.isEmpty()) return Optional.empty();
        Row p = profile.get();

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
                p.version(),
                p.speaker(),
                p.bible(),
                p.rules(),
                p.safety(),
                p.expressions(),
                LlmJson.JSON.readValue(p.guide(), STRING_MAP),
                tones,
                p.studyStyle(),
                p.breakStyle(),
                lines,
                LlmJson.JSON.readValue(p.samples(), SAMPLES),
                scores,
                habits,
                LlmJson.JSON.readValue(p.lore(), LORE)));
    }

    /**
     * 이 캐릭터의 설정을 통째로 바꾼다 (실험실 저장 · 시드). 버전도 c.version()으로
     *
     * @param labEditedAt 실험실 저장이면 그때, 파일에서 불러오면 null
     */
    @Transactional
    public void replace(CharacterContent c, Instant labEditedAt) {
        long id = c.characterId();
        // 같은 폴더(code)가 다른 캐릭터에 붙어 있었으면 그것도 지운다 (reseed가 다른 캐릭터로 옮길 때 code UNIQUE 위반 방지)
        jdbc.sql("DELETE FROM character_profiles WHERE character_id = ? OR code = ?")
                .params(id, c.code())
                .update(); // 하위 행은 CASCADE
        jdbc.sql("""
                        INSERT INTO character_profiles
                            (character_id, code, content_version, speaker, bible, rules, safety, expressions,
                             expression_guide, stage_tones, study_style, break_style, samples, lore, lab_edited_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ARRAY(SELECT jsonb_array_elements_text(CAST(? AS jsonb))),
                                CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?)""")
                .params(
                        id,
                        c.code(),
                        c.version(),
                        c.speaker(),
                        c.bible(),
                        c.rules(),
                        c.safety(),
                        LlmJson.write(c.expressions()),
                        LlmJson.write(c.expressionGuide()),
                        LlmJson.write(c.stageTones()),
                        c.studyStyle(),
                        c.breakStyle(),
                        LlmJson.write(c.samples() == null ? List.of() : c.samples()),
                        LlmJson.write(c.lore()),
                        labEditedAt == null ? null : java.sql.Timestamp.from(labEditedAt))
                .update();
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

    private static List<String> strings(ResultSet rs, String column) throws SQLException {
        Array a = rs.getArray(column);
        return a == null ? List.of() : Arrays.asList((String[]) a.getArray());
    }
}
