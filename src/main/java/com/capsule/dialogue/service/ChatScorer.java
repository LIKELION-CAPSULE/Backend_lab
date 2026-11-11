package com.capsule.dialogue.service;

import com.capsule.common.settings.RuntimeSettings;
import com.capsule.dialogue.config.DialogueProperties;
import com.capsule.dialogue.config.DialogueProperties.ScoreRule;
import com.capsule.dialogue.dto.CharacterContent;
import com.capsule.relation.api.RelationTypes.ChatScore;
import com.capsule.relation.api.RelationTypes.SignalType;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 대화 한 번의 점수 (PM 2026-11-04): 결정 모델이 고른 말의 종류(ChatReplier)에 점수표와 같은 세션 안 규칙으로 점수를 정한다.
 * 하루 상한 · 0~100 · 심한 말 뒤 오름 멈춤은 relation이 한다.
 */
@Component
@RequiredArgsConstructor
public class ChatScorer {

    private final RuntimeSettings<DialogueProperties> props;
    private final CharacterContents contents;

    /**
     * @param earlier 이번 세션에서 앞선 사용자 말들의 종류 (오래된 것부터, 모르는 것은 null)
     */
    // TODO(judgment): J-06 대화 점수표 · 반복 규칙 (PM 임시값: 캐릭터 설정 character_scores, 없으면 application.yml chat-scores)
    public ChatScore score(long characterId, SignalType signal, List<SignalType> earlier) {
        Map<SignalType, ScoreRule> table = contents.find(characterId)
                .map(CharacterContent::scores)
                .filter(s -> !s.isEmpty())
                .orElse(props.current().chatScores());
        ScoreRule rule = table.get(signal);
        if (rule == null) return new ChatScore(signal, 0, 0);
        boolean seen = earlier.contains(signal);
        if (rule.oncePerSession() && seen) return new ChatScore(signal, 0, 0);
        int points = seen && rule.repeatPoints() != null ? rule.repeatPoints() : rule.points();
        if (points > 0) {
            List<SignalType> window = earlier.subList(
                    Math.max(0, earlier.size() - (props.current().chatRepeatWindow() - 1)), earlier.size());
            if (window.stream().filter(signal::equals).count()
                    >= props.current().chatRepeatLimit()) points = 0;
        }
        return new ChatScore(signal, points, rule.lossPercent());
    }
}
