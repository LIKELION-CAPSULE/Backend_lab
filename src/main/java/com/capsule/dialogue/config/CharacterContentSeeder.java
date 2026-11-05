package com.capsule.dialogue.config;

import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.content.api.RoomQuery;
import com.capsule.dialogue.config.DialogueProperties.ScoreRule;
import com.capsule.dialogue.dto.CharacterContent;
import com.capsule.dialogue.dto.CharacterContent.Line;
import com.capsule.dialogue.dto.CharacterContent.Sample;
import com.capsule.dialogue.repository.CharacterContentRepository;
import com.capsule.relation.api.RelationTypes.SignalType;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * classpath:characters/<code>/의 처음 값(profile.yml)을 DB 캐릭터 설정으로 불러온다 (ADR 0022 결정 15).
 * 시작할 때 DB에 그 캐릭터 설정(code)이 없으면 넣고, 있으면 버전을 본다: profile.yml의 version이 DB의 content_version보다
 * 높으면 파일로 다시 불러오고(실험실에서 고친 값은 덮임), 같거나 낮으면 그대로 둔다. 실험실 저장은 버전을 바꾸지 않으므로
 * 문구를 git에서 고쳤으면 version을 올려야 공용 DB · 개발 서버에 들어간다 (그곳은 실험실 reseed가 꺼져 있다).
 * 붙을 characters 행(content 모듈)은 profile.yml의 characterName으로 찾는다 (id는 DB마다 다르다). 없으면 건너뛴다.
 * 버전과 상관없이 다시 불러오려면 실험실 reseed. 파일이 깨졌거나 DB가 준비되지 않았어도 앱은 뜬다: 그 캐릭터만 건너뛰고(설정이 없으면 SIMPLE로 답함) 로그를 남긴다.
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
            Integer version,
            String characterName,
            String speaker,
            String safety,
            List<String> expressions,
            Map<String, String> expressionGuide,
            Map<Integer, String> stageTones,
            String studyStyle,
            String breakStyle,
            String bible,
            String rules,
            Map<SignalType, ScoreRule> scores,
            Map<String, String> habits,
            Map<String, List<LineFile>> lines,
            Map<Integer, List<SampleFile>> samples,
            List<CharacterContent.Lore> lore) {}

    record LineFile(String text, String expression, Integer minStage) {}

    record SampleFile(String user, String reply) {}

    private final CharacterContentRepository contents;
    private final RoomQuery rooms;

    CharacterContentSeeder(CharacterContentRepository contents, RoomQuery rooms) {
        this.contents = contents;
        this.rooms = rooms;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        for (Resource r :
                new PathMatchingResourcePatternResolver().getResources("classpath*:characters/*/profile.yml")) {
            String code = folder(r);
            try {
                seed(code, false);
            } catch (RuntimeException e) {
                log.error("캐릭터 설정 {}을 불러오지 못해 건너뜀 (앱은 계속 뜬다)", code, e);
            }
        }
    }

    /**
     * @return 불러온 캐릭터 id. DB 버전이 파일 버전 이상이거나(overwrite가 아닐 때) 붙을 캐릭터 행이 없으면 빈 값.
     * overwrite(실험실 reseed)면 버전과 상관없이 git 값으로 덮는다.
     */
    public Optional<Long> seed(String code, boolean overwrite) {
        ProfileFile p = profile(code);
        int fileVersion = p.version() == null ? 0 : p.version();
        Optional<Integer> dbVersion = contents.versionOf(code);
        if (!overwrite && dbVersion.isPresent() && dbVersion.get() >= fileVersion) {
            log.info("캐릭터 설정 {}은 그대로 둠 (DB 버전 {} ≥ 파일 버전 {})", code, dbVersion.get(), fileVersion);
            return Optional.empty();
        }
        if (p.characterName() == null || p.characterName().isBlank())
            throw new IllegalStateException("characters/" + code + "/profile.yml에 characterName이 없음");
        Optional<Long> id = characterIdOf(p.characterName());
        if (id.isEmpty()) {
            log.warn("이름이 {}인 활성 캐릭터 행이 없어 {} 설정을 건너뜀 (그 캐릭터는 SIMPLE로 답함)", p.characterName(), code);
            return Optional.empty();
        }
        // 그 캐릭터에 다른 폴더(code)의 설정이 이미 있으면 덮지 않는다: 실험실에서 고친 DB 원본을 지키려고
        Optional<String> attached = contents.codeOf(id.get());
        if (!overwrite && attached.isPresent() && !attached.get().equals(code)) {
            log.warn("캐릭터 {}에 다른 설정({})이 이미 있어 {}을 불러오지 않음 (덮으려면 실험실 reseed)", id.get(), attached.get(), code);
            return Optional.empty();
        }
        contents.replace(build(code, p, id.get()));
        if (overwrite) log.info("캐릭터 설정을 파일로 덮음 (reseed): {} 버전 {} → 캐릭터 {}", code, fileVersion, id.get());
        else if (dbVersion.isPresent())
            log.info("캐릭터 설정을 새 버전으로 다시 불러옴: {} 버전 {} → {} (캐릭터 {})", code, dbVersion.get(), fileVersion, id.get());
        else log.info("캐릭터 설정을 불러옴: {} 버전 {} → 캐릭터 {}", code, fileVersion, id.get());
        return id;
    }

    /** 활성 룸의 활성 캐릭터 중 이름이 같은(앞뒤 공백 무시) 하나. 여럿이면 어느 쪽인지 모르므로 빈 값 */
    private Optional<Long> characterIdOf(String name) {
        List<Long> ids = rooms.activeRooms().stream()
                .flatMap(r -> r.characters().stream())
                .filter(ch -> ch.name().strip().equals(name.strip()))
                .map(CharacterInfo::id)
                .distinct()
                .toList();
        if (ids.size() > 1) log.warn("이름이 {}인 캐릭터가 여럿이라 고르지 않음: {}", name, ids);
        return ids.size() == 1 ? Optional.of(ids.getFirst()) : Optional.empty();
    }

    /** profile.yml → 캐릭터 id에 붙일 설정 */
    public static CharacterContent load(String code, long characterId) {
        return build(code, profile(code), characterId);
    }

    private static ProfileFile profile(String code) {
        return JSON.convertValue(new Yaml().load(read("characters/" + code + "/profile.yml")), ProfileFile.class);
    }

    /** classpath 텍스트 파일 (PromptSeeder도 씀) */
    static String read(String path) {
        try (InputStream in = CharacterContentSeeder.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("리소스 없음: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static CharacterContent build(String code, ProfileFile p, long characterId) {
        List<Line> lines = new ArrayList<>();
        (p.lines() == null ? Map.<String, List<LineFile>>of() : p.lines()).forEach((situation, list) -> {
            for (int i = 0; i < list.size(); i++) {
                LineFile l = list.get(i);
                lines.add(new Line(situation, l.minStage() == null ? 1 : l.minStage(), i, l.text(), l.expression()));
            }
        });
        List<Sample> samples = new ArrayList<>();
        (p.samples() == null ? Map.<Integer, List<SampleFile>>of() : p.samples())
                .forEach((stage, list) -> list.forEach(s -> samples.add(new Sample(stage, s.user(), s.reply()))));
        return new CharacterContent(
                characterId,
                code,
                p.version() == null ? 0 : p.version(),
                p.speaker(),
                p.bible().strip(),
                p.rules().strip(),
                p.safety(),
                p.expressions(),
                p.expressionGuide() == null ? Map.of() : p.expressionGuide(),
                p.stageTones() == null ? Map.of() : p.stageTones(),
                p.studyStyle().strip(),
                p.breakStyle().strip(),
                lines,
                samples,
                p.scores() == null ? Map.of() : p.scores(),
                p.habits() == null ? Map.of() : p.habits(),
                p.lore());
    }

    /** classpath:characters/<code>/profile.yml → code */
    private static String folder(Resource r) throws IOException {
        String path = java.net.URLDecoder.decode(r.getURL().getPath(), StandardCharsets.UTF_8);
        String[] parts = path.split("/");
        return parts[parts.length - 2];
    }
}
