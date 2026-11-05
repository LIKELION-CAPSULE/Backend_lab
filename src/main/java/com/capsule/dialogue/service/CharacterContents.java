package com.capsule.dialogue.service;

import com.capsule.dialogue.config.DialogueProperties;
import com.capsule.dialogue.dto.CharacterContent;
import com.capsule.dialogue.dto.ReactionLine;
import com.capsule.dialogue.repository.CharacterContentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * DB 캐릭터 설정 읽기 (ADR 0022 결정 15). 한 턴에 여러 곳(답장 고정 블록 · 점수표 · 상황 대사 · 표정)이 읽으므로 잠깐 들고 있는다.
 * 실험실에서 고치면 바로 비운다. ponytail: 서버 1대 전제(ADR 0011)의 메모리 캐시. 여러 대면 TTL만으로 맞춰진다
 */
@Component
public class CharacterContents {

    private record Cached(Optional<CharacterContent> content, Instant at) {}

    private final CharacterContentRepository repository;
    private final Clock clock;
    private final Duration ttl;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();
    // ponytail: 순서 자리를 메모리에 둔다. 재시작하면 첫 대사부터. 여러 대가 되면 세션별 자리를 DB로
    /** 순서대로 돌려 쓰는 상황 대사의 다음 자리. 세션 수만큼 늘어나지 않게 content-max-cursors를 넘으면 오래된 것부터 버린다 */
    private final Map<String, Integer> cursors;

    CharacterContents(CharacterContentRepository repository, Clock clock, DialogueProperties props) {
        this.repository = repository;
        this.clock = clock;
        this.ttl = props.contentCacheTtl();
        int maxCursors = props.contentMaxCursors();
        this.cursors = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
                return size() > maxCursors;
            }
        };
    }

    public Optional<CharacterContent> find(long characterId) {
        Instant now = Instant.now(clock);
        Cached c = cache.get(characterId);
        if (c == null || c.at().plus(ttl).isBefore(now)) {
            c = new Cached(repository.find(characterId), now);
            cache.put(characterId, c);
        }
        return c.content();
    }

    /** 실험실 저장. 버전은 보낸 값이 아니라 DB 값 그대로: profile.yml version을 올리면 다음 시작 때 파일이 이긴다 */
    public void replace(CharacterContent content) {
        // 시더와 같은 기준(code)으로 읽는다: 다른 캐릭터로 옮겨 저장해도 버전이 0으로 떨어지지 않게
        int version = repository.versionOf(content.code()).orElse(0);
        repository.replace(content.withVersion(version));
        cache.remove(content.characterId());
    }

    public void evict(long characterId) {
        cache.remove(characterId);
    }

    /**
     * 상황 대사를 순서대로 하나 (PM 확정: 미리 쓴 대사를 돌려 쓰기). 강도 칸(AWAY_2)에 대사가 없으면 기본 칸(AWAY).
     * 이 캐릭터 설정에 그 상황 대사가 없으면 빈 값 (부르는 쪽이 공통 고정 대사로)
     *
     * @param rotationKey 같은 자리를 이어 쓰는 단위 (예: 세션 id)
     */
    public Optional<ReactionLine> next(
            long characterId, String situation, String baseSituation, int stage, String rotationKey, String nickname) {
        Optional<CharacterContent> found = find(characterId);
        if (found.isEmpty()) return Optional.empty();
        List<CharacterContent.Line> lines = found.get().linesFor(situation, stage);
        String used = situation;
        if (lines.isEmpty() && baseSituation != null) {
            lines = found.get().linesFor(baseSituation, stage);
            used = baseSituation;
        }
        if (lines.isEmpty()) return Optional.empty();
        String key = characterId + "/" + used + "/" + rotationKey;
        int i;
        synchronized (cursors) {
            i = cursors.getOrDefault(key, 0);
            cursors.put(key, i + 1);
        }
        CharacterContent.Line l = lines.get(i % lines.size());
        return Optional.of(new ReactionLine(PromptVars.named(l.text(), nickname), l.expression()));
    }
}
