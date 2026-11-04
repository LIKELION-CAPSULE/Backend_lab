package com.capsule.dialogue.service;

import com.capsule.common.error.ApiException;
import com.capsule.dialogue.config.ContentLoader;
import com.capsule.dialogue.domain.PromptKey;
import com.capsule.dialogue.domain.PromptVersion;
import com.capsule.dialogue.repository.PromptVersionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

/**
 * 프롬프트 레지스트리 (ADR 0022). 코드는 키(PromptKey)로 찾고, 본문은 DB 버전이 정한다 → 배포 없이 프롬프트를 바꾸고 되돌린다.
 * 쓰는 버전: 실험에서 고정한 버전(pinned) → 캐릭터 전용 ACTIVE → 공통 ACTIVE.
 * LLM 호출마다 쓴 버전 id가 llm_usages.prompt_version_ids에 남아서, 버전별 품질·비용을 비교할 수 있다.
 */
@Service
public class PromptService {

    private static final Pattern VAR = Pattern.compile("\\{\\{(\\w+)}}");

    private final PromptVersionRepository versions;
    private final Clock clock;

    PromptService(PromptVersionRepository versions, Clock clock) {
        this.versions = versions;
        this.clock = clock;
    }

    public PromptVersion resolve(PromptKey key, long characterId, Map<String, Long> pinned) {
        Long pin = pinned.get(key.key());
        if (pin != null)
            return versions.findById(pin)
                    .filter(v -> v.promptKey().equals(key.key()))
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "PROMPT_VERSION_NOT_FOUND"));
        return find(key, characterId)
                .orElseThrow(
                        () -> new IllegalStateException("ACTIVE 프롬프트 없음: " + key.key() + " (캐릭터 " + characterId + ")"));
    }

    public Optional<PromptVersion> find(PromptKey key, long characterId) {
        return versions.findActiveFor(key.key(), characterId).stream().findFirst();
    }

    /** 이 캐릭터가 지금 쓰는 프롬프트 (키 → 버전). 없는 키는 빠진다 */
    public Map<String, PromptVersion> activeSet(long characterId) {
        Map<String, PromptVersion> set = new LinkedHashMap<>();
        for (PromptKey k : PromptKey.values()) find(k, characterId).ifPresent(v -> set.put(k.key(), v));
        return set;
    }

    public List<PromptVersion> search(String key, Long characterId) {
        return versions.search(key, characterId);
    }

    public PromptVersion get(long id) {
        return versions.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROMPT_VERSION_NOT_FOUND"));
    }

    /** 새 버전(DRAFT). activate면 바로 켠다 */
    @Transactional
    public PromptVersion create(PromptKey key, Long characterId, String body, String note, boolean activate) {
        if (key.characterOnly() && characterId == null)
            throw new ApiException(HttpStatus.BAD_REQUEST, "CHARACTER_REQUIRED");
        if (body == null || body.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_PROMPT");
        if (key == PromptKey.FIXED_LINES) checkYaml(body);
        int next = versions.maxVersion(key.key(), characterId) + 1;
        PromptVersion v =
                versions.saveAndFlush(new PromptVersion(key.key(), characterId, next, body, note, Instant.now(clock)));
        return activate ? activate(v.id()) : v;
    }

    /** 이 버전을 켜고 같은 (키, 캐릭터)의 이전 ACTIVE는 ARCHIVED. 되돌리기도 이것 (예전 버전을 다시 켬) */
    @Transactional
    public PromptVersion activate(long id) {
        PromptVersion v = get(id);
        versions.findActiveExact(v.promptKey(), v.characterId())
                .filter(a -> !a.id().equals(v.id()))
                .ifPresent(a -> {
                    a.archive();
                    versions.saveAndFlush(a); // 새 ACTIVE보다 먼저 내보낸다 (ACTIVE 하나 제약)
                });
        v.activate(Instant.now(clock));
        return versions.saveAndFlush(v);
    }

    /** 대화 엔진 파일 네 개를 한 번에 올린다. 엔진이 읽을 수 있는 내용인지 먼저 확인한다 */
    @Transactional
    public List<PromptVersion> importEnginePack(
            long characterId,
            String characterYml,
            String actor,
            String director,
            String judge,
            String note,
            boolean activate) {
        try {
            ContentLoader.fromTexts(characterYml, actor, director, judge);
        } catch (RuntimeException e) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ENGINE_PACK",
                    Map.<String, Object>of("reason", String.valueOf(e.getMessage())));
        }
        return List.of(
                create(PromptKey.ENGINE_CHARACTER, characterId, characterYml, note, activate),
                create(PromptKey.ENGINE_ACTOR, characterId, actor, note, activate),
                create(PromptKey.ENGINE_DIRECTOR, characterId, director, note, activate),
                create(PromptKey.ENGINE_JUDGE, characterId, judge, note, activate));
    }

    /** {{이름}} 자리를 채운다. 모르는 이름은 그대로 둔다 (실험실 원문에서 빠진 값이 보이도록) */
    public static String render(String body, Map<String, String> vars) {
        Matcher m = VAR.matcher(body);
        return m.replaceAll(r -> Matcher.quoteReplacement(vars.getOrDefault(r.group(1), r.group())));
    }

    private static void checkYaml(String body) {
        try {
            new Yaml().load(body);
        } catch (RuntimeException e) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_YAML",
                    Map.<String, Object>of("reason", String.valueOf(e.getMessage())));
        }
    }
}
