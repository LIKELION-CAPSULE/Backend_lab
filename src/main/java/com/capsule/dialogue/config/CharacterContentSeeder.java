package com.capsule.dialogue.config;

import com.capsule.content.api.CharacterQuery;
import com.capsule.dialogue.config.DialogueProperties.ScoreRule;
import com.capsule.dialogue.dto.CharacterContent;
import com.capsule.dialogue.dto.CharacterContent.Event;
import com.capsule.dialogue.dto.CharacterContent.Example;
import com.capsule.dialogue.dto.CharacterContent.Line;
import com.capsule.dialogue.dto.CharacterContent.Lore;
import com.capsule.dialogue.dto.CharacterContent.Move;
import com.capsule.dialogue.dto.CharacterContent.World;
import com.capsule.dialogue.engine.Content;
import com.capsule.dialogue.repository.CharacterContentRepository;
import com.capsule.relation.api.RelationTypes.SignalType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * classpath:characters/<code>/의 처음 값(profile.yml + character.yml)을 DB 캐릭터 설정으로 불러온다 (ADR 0022 결정 15).
 * DB에 그 캐릭터 설정이 없을 때만 넣는다: 그 뒤로는 DB가 원본이고 실험실에서 고친다 (git 파일을 고쳐도 덮지 않음).
 * 다시 불러오려면 실험실 reseed. characters 행(content 모듈)이 없으면 건너뛴다 (운영에 아직 캐릭터 행이 없을 때).
 */
@Component
public class CharacterContentSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CharacterContentSeeder.class);
    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES) // 점수표에서 빠진 칸은 0 · false
            .build();

    /** profile.yml 모양 */
    record ProfileFile(
            long characterId,
            String speaker,
            String safety,
            WorldFile world,
            List<String> expressions,
            Map<String, String> expressionFallbacks,
            Map<Integer, String> stageTones,
            String studyStyle,
            String breakStyle,
            String bible,
            String rules,
            Map<String, List<SignalType>> moveSignals,
            Map<String, List<String>> moveModes,
            List<String> skipMoves,
            Map<SignalType, ScoreRule> scores,
            Map<String, String> habits,
            Map<String, List<LineFile>> lines) {}

    record WorldFile(String code, String name, String setting) {}

    record LineFile(String text, String expression, Integer minStage) {}

    private final CharacterContentRepository contents;
    private final CharacterQuery characters;

    CharacterContentSeeder(CharacterContentRepository contents, CharacterQuery characters) {
        this.contents = contents;
        this.characters = characters;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath*:characters/*/profile.yml"))
            seed(folder(r), false);
    }

    /** @return 불러왔으면 true. overwrite면 DB에 있어도 git의 처음 값으로 덮는다 */
    public boolean seed(String code, boolean overwrite) {
        CharacterContent c = load(code);
        if (characters.find(c.characterId()).isEmpty()) {
            log.info("캐릭터 {} 행이 없어 {} 설정을 건너뜀", c.characterId(), code);
            return false;
        }
        if (!overwrite && contents.exists(c.characterId())) return false;
        contents.replace(c);
        log.info("캐릭터 설정을 불러옴: {} → 캐릭터 {}", code, c.characterId());
        return true;
    }

    /** profile.yml + character.yml(대화 기술 · 세계 지식 · 세계 사건) → 캐릭터 설정 */
    public static CharacterContent load(String code) {
        String base = "characters/" + code + "/";
        ProfileFile p = JSON.convertValue(new Yaml().load(ContentLoader.read(base + "profile.yml")), ProfileFile.class);
        Content.Character engine = ContentLoader.fromClasspath(code).character();
        List<String> skip = p.skipMoves() == null ? List.of() : p.skipMoves();
        Map<String, List<SignalType>> signals = p.moveSignals() == null ? Map.of() : p.moveSignals();
        Map<String, List<String>> modes = p.moveModes() == null ? Map.of() : p.moveModes();
        List<Move> moves = engine.moves().stream()
                .filter(m -> !skip.contains(m.id()))
                .map(m -> new Move(
                        m.id(),
                        m.name(),
                        m.how(),
                        signals.getOrDefault(m.id(), List.of()),
                        modes.getOrDefault(m.id(), List.of()),
                        m.examples().stream()
                                .map(e -> new Example(e.user(), e.reply()))
                                .toList()))
                .toList();
        List<Lore> lore = engine.lore().stream()
                .map(l -> new Lore(l.id(), l.title(), l.keys(), l.text()))
                .toList();
        List<Event> events = engine.events().stream()
                .map(e -> new Event(e.id(), e.text(), e.hook(), e.lore(), 1))
                .toList();
        List<Line> lines = new ArrayList<>();
        (p.lines() == null ? Map.<String, List<LineFile>>of() : p.lines()).forEach((situation, list) -> {
            for (int i = 0; i < list.size(); i++) {
                LineFile l = list.get(i);
                lines.add(new Line(situation, l.minStage() == null ? 1 : l.minStage(), i, l.text(), l.expression()));
            }
        });
        return new CharacterContent(
                p.characterId(),
                code,
                p.speaker(),
                p.bible().strip(),
                p.rules().strip(),
                p.safety(),
                p.expressions(),
                p.expressionFallbacks() == null ? Map.of() : p.expressionFallbacks(),
                p.stageTones() == null ? Map.of() : p.stageTones(),
                p.studyStyle().strip(),
                p.breakStyle().strip(),
                new World(
                        p.world().code(), p.world().name(), p.world().setting().strip(), lore, events),
                moves,
                lines,
                p.scores() == null ? Map.of() : p.scores(),
                p.habits() == null ? Map.of() : p.habits());
    }

    /** classpath:characters/<code>/profile.yml → code */
    private static String folder(Resource r) throws IOException {
        String path = java.net.URLDecoder.decode(r.getURL().getPath(), StandardCharsets.UTF_8);
        String[] parts = path.split("/");
        return parts[parts.length - 2];
    }
}
