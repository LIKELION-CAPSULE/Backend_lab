package com.capsule.dialogue.service;

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
 * DB 캐릭터 설정 읽기 (ADR 0022 결정 15). 한 턴에 여러 곳(장면 카드 · 점수표 · 상황 대사 · 표정)이 읽으므로 잠깐 들고 있는다.
 * 실험실에서 고치면 바로 비운다. ponytail: 서버 1대 전제(ADR 0011)의 메모리 캐시. 여러 대면 TTL만으로 맞춰진다
 */
@Component
public class CharacterContents {

    private static final Duration TTL = Duration.ofSeconds(30);
    /** 순서대로 돌려 쓰는 상황 대사의 다음 자리. 세션 수만큼 늘어나지 않게 오래된 것부터 버린다 */
    private static final int MAX_CURSORS = 10_000;

    private record Cached(Optional<CharacterContent> content, Instant at) {}

    private final CharacterContentRepository repository;
    private final Clock clock;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();
    // ponytail: 순서 자리를 메모리에 둔다. 재시작하면 첫 대사부터. 여러 대가 되면 세션별 자리를 DB로
    private final Map<String, Integer> cursors = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return size() > MAX_CURSORS;
        }
    };

    CharacterContents(CharacterContentRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Optional<CharacterContent> find(long characterId) {
        Instant now = Instant.now(clock);
        Cached c = cache.get(characterId);
        if (c == null || c.at().plus(TTL).isBefore(now)) {
            c = new Cached(repository.find(characterId), now);
            cache.put(characterId, c);
        }
        return c.content();
    }

    public void replace(CharacterContent content) {
        repository.replace(content);
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
        String text = l.text().replace("{name}", nickname == null || nickname.isBlank() ? "너" : nickname);
        return Optional.of(new ReactionLine(text, l.expression()));
    }
}
