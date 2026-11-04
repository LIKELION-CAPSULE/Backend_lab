package com.capsule.dialogue.config;

import com.capsule.dialogue.engine.Content;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * classpath:characters/{id}/ 아래의 character.yml, actor.md, director.md, judge.md를 읽어 엔진 콘텐츠를 만든다.
 * Spring 없이도 쓸 수 있게 정적 메서드로 둔다(단위 테스트에서 그대로 사용).
 * YAML은 Spring Boot가 이미 가져오는 SnakeYAML로 읽고 Jackson으로 레코드에 옮긴다(명세의 jackson-dataformat-yaml 의존성을 더하지 않으려고).
 */
public final class ContentLoader {
    private ContentLoader() {}

    public record CharacterFile(
            String id,
            String displayName,
            String speaker,
            String promptVersion,
            List<String> emotions,
            String greetingWithName,
            String greetingNoName,
            Map<String, List<String>> fixedLines,
            List<Content.Move> moves,
            List<Content.Beat> beats,
            List<Content.LoreCard> lore,
            List<Content.WorldEvent> events) {}

    public record Loaded(Content.Character character, String promptVersion) {}

    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public static Loaded fromClasspath(String characterId) {
        String base = "characters/" + characterId + "/";
        return fromTexts(
                read(base + "character.yml"),
                read(base + "actor.md"),
                read(base + "director.md"),
                read(base + "judge.md"));
    }

    /** 파일 네 개의 내용으로 만든다. 서비스는 DB 프롬프트 버전(engine.*)에서 읽어 이걸 부른다. 콘텐츠가 틀리면 IllegalStateException */
    public static Loaded fromTexts(String characterYml, String actor, String director, String judge) {
        Object tree = new Yaml().load(characterYml);
        CharacterFile f = JSON.convertValue(tree, CharacterFile.class);
        if (f.moves() == null || f.beats() == null || f.lore() == null || f.events() == null || f.fixedLines() == null)
            throw new IllegalStateException("character.yml에 moves · beats · lore · events · fixedLines가 모두 있어야 함");
        Content.Character c = new Content.Character(
                f.id(),
                f.displayName(),
                f.speaker(),
                actor.strip(),
                director.strip(),
                judge.strip(),
                f.moves(),
                f.beats(),
                f.lore(),
                f.events(),
                f.emotions(),
                f.fixedLines(),
                f.greetingWithName(),
                f.greetingNoName());
        validate(c);
        return new Loaded(c, f.promptVersion());
    }

    /** 콘텐츠 실수(없는 수 id 참조 등)를 앱 시작 시점에 잡는다. */
    static void validate(Content.Character c) {
        for (String required : List.of(
                "yes_and",
                "followup",
                "gossip",
                "secret_study",
                "sincere",
                "refuse",
                "callback",
                "pay_loop",
                "escalate")) if (!c.hasMove(required)) throw new IllegalStateException("필수 수가 없음: " + required);
        if (!c.directorTemplate().contains("{{MOVES}}"))
            throw new IllegalStateException("director.md에 {{MOVES}} 자리가 없음");
        for (Content.WorldEvent e : c.events())
            if (e.lore() != null && c.loreCard(e.lore()).isEmpty())
                throw new IllegalStateException("사건 " + e.id() + "의 lore가 없음: " + e.lore());
        for (String k : List.of("break", "resume", "away", "back"))
            if (c.fixedLines().getOrDefault(k, List.of()).isEmpty()) throw new IllegalStateException("고정 대사 없음: " + k);
    }

    static String read(String path) {
        try (InputStream in = ContentLoader.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("리소스 없음: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
