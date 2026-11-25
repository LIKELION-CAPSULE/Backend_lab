package com.capsule.dialogue.service;

import com.capsule.content.api.ContentTypes.UnlockInfo;
import com.capsule.content.api.UnlockQuery;
import com.capsule.relation.api.RelationQuery;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 아직 안 열린 해금 표정 (api.md 19 emotion, 2026-11-26): 친밀도 해금 콘텐츠(content character_contents EXPRESSION) 중 이 사용자에게
 * 열리지 않은 것(relation user_unlocked_contents). 표정 고르기(ExpressionPicker)의 보기에서 뺀다. 해금 목록에 없는 표정은 늘 쓴다
 */
@Component
@RequiredArgsConstructor
public class LockedExpressions {

    static final String EXPRESSION = "EXPRESSION";

    private final UnlockQuery unlocks;
    private final RelationQuery relations;

    /** 사용자를 모르면(실험실 · 평가) 빈 집합. 같은 표정이 여러 해금에 있으면 하나라도 열렸으면 쓸 수 있음 */
    public Set<String> of(Long userId, long characterId) {
        if (userId == null) return Set.of();
        List<UnlockInfo> expressions = unlocks.between(characterId, 0, Integer.MAX_VALUE).stream()
                .filter(u -> EXPRESSION.equals(u.type()) && u.expressionName() != null)
                .toList();
        if (expressions.isEmpty()) return Set.of();
        Set<Long> open = relations.unlockedContentIds(userId);
        Set<String> locked = new HashSet<>(), opened = new HashSet<>();
        for (UnlockInfo u : expressions) (open.contains(u.id()) ? opened : locked).add(u.expressionName());
        locked.removeAll(opened);
        return locked;
    }
}
