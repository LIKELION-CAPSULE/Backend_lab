package com.capsule.dialogue.service;

import com.capsule.dialogue.config.DialogueProperties;
import com.capsule.dialogue.config.DialogueProperties.ScoreRule;
import com.capsule.dialogue.dto.CharacterContent;
import com.capsule.dialogue.engine.Rules;
import com.capsule.relation.api.RelationTypes.ChatScore;
import com.capsule.relation.api.RelationTypes.SignalType;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 대화 한 번의 점수 (A안, PM 2026-11-04): 말 패턴으로 종류를 고르고, 점수표와 같은 세션 안 규칙으로 점수를 정한다.
 * 하루 상한 · 0~100 · 심한 말 뒤 오름 멈춤은 relation이 한다.
 */
@Component
public class ChatScorer {

    private final DialogueProperties props;
    private final CharacterContents contents;

    ChatScorer(DialogueProperties props, CharacterContents contents) {
        this.props = props;
        this.contents = contents;
    }

    /** 앞에 걸리는 것이 이긴다: 심한 말 > 무례 > 고민 > 고마움 > 칭찬 > 불평 > 단답 */
    static SignalType detect(String text) {
        String t = text == null ? "" : text.strip();
        if (t.isEmpty()) return SignalType.NEUTRAL;
        if (find(Rules.HATE_OR_SEXUAL, t)) return SignalType.HATE_OR_SEXUAL;
        if (find(Rules.RUDE, t)) return SignalType.RUDE;
        if (find(Rules.NEGATIVE, t)) return SignalType.CONFIDE;
        if (find(Rules.THANKS, t)) return SignalType.THANKS;
        if (find(Rules.COMPLIMENT, t)) return SignalType.COMPLIMENT;
        if (find(Rules.COMPLAINT, t)) return SignalType.COMPLAINT;
        // 엔진 Analysis.analyzeUser의 단답 판단과 같다
        if (t.replaceAll("\\s", "").length() <= 3 || find(Rules.LOW_EFFORT, t)) return SignalType.LOW_EFFORT;
        return SignalType.NEUTRAL;
    }

    /**
     * @param earlier 이번 세션에서 앞선 사용자 말들의 종류 (오래된 것부터, 모르는 것은 null)
     */
    // TODO(judgment): J-06 대화 점수표 · 반복 규칙 (PM 임시값: 캐릭터 설정 character_scores, 없으면 application.yml chat-scores)
    public ChatScore score(long characterId, SignalType signal, List<SignalType> earlier) {
        Map<SignalType, ScoreRule> table = contents.find(characterId)
                .map(CharacterContent::scores)
                .filter(s -> !s.isEmpty())
                .orElse(props.chatScores());
        ScoreRule rule = table.get(signal);
        if (rule == null) return new ChatScore(signal, 0, 0);
        boolean seen = earlier.contains(signal);
        if (rule.oncePerSession() && seen) return new ChatScore(signal, 0, 0);
        int points = seen && rule.repeatPoints() != null ? rule.repeatPoints() : rule.points();
        if (points > 0) {
            List<SignalType> window =
                    earlier.subList(Math.max(0, earlier.size() - (props.chatRepeatWindow() - 1)), earlier.size());
            if (window.stream().filter(signal::equals).count() >= props.chatRepeatLimit()) points = 0;
        }
        return new ChatScore(signal, points, rule.lossPercent());
    }

    private static boolean find(Pattern p, String s) {
        return p.matcher(s).find();
    }
}
