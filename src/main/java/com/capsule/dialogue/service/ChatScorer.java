package com.capsule.dialogue.service;

import com.capsule.common.settings.RuntimeSettings;
import com.capsule.dialogue.config.DialogueProperties;
import com.capsule.dialogue.config.DialogueProperties.ScoreRule;
import com.capsule.dialogue.dto.CharacterContent;
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

    private static final int U = Pattern.UNICODE_CHARACTER_CLASS;

    private static final Pattern LOW_EFFORT = Pattern.compile(
            "^(ㅇㅇ+|ㅇ|응+|어+|ㄴㄴ|ㄴ|몰라|모름|ㅋ+|ㅎ+|ㅇㅋ|오키|ok|ㄱㄱ|넵|네|아니|그래|공부\\s*중|피곤|끝|ㅠ+|ㅜ+)[.!~?ㅋㅎ]*$",
            U | Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPLAINT = Pattern.compile("(재미없|노잼|잔소리|자꾸|그만 ?좀|엄마여|엄마야|짜증나|지겹|맨날)", U);
    private static final Pattern NEGATIVE = Pattern.compile("(힘들|우울|지쳐|지친|그만두|포기|죽겠|무서|불안)", U);
    private static final Pattern RUDE = Pattern.compile("(ㅗ|ㅅㅂ|씨발|시발|꺼져|닥쳐|미친|병신|ㅈ같)", U);
    private static final Pattern COMPLIMENT =
            Pattern.compile("(이쁘|예쁘|귀엽|멋있|멋지|잘한다|천재|대단|똑똑|잘생(겼|긴|김)|머리 ?(진짜 ?|완전 ?|좀 ?)?좋)", U);
    /** 호감도 신호 THANKS (PM 2026-11-04) */
    private static final Pattern THANKS = Pattern.compile("(고마워|고맙|감사|땡큐|ㄱㅅ|ㄳ|thank)", U | Pattern.CASE_INSENSITIVE);
    /**
     * 호감도 신호 HATE_OR_SEXUAL 후보. 걸리면 결정 모델이 한 번 더 확인한다(오탐이면 RUDE).
     * 공부 대화에 흔한 말(자위대 · 자위권 · 한남동 · 사과 따먹었어)은 걸리지 않게 뒤를 막는다 (ChatScorerTest).
     * ponytail: 짧은 단어 목록. 놓치는 말은 실험실 평가로 모아 늘린다
     */
    private static final Pattern HATE_OR_SEXUAL = Pattern.compile(
            "(섹스|섹드립|야동|자위(?!대|권|함)|따먹(자|을래|고 ?싶|어 ?줄)|가슴 ?(만지|보여)|벗어 ?봐|몸매 ?(좋|보여|야하|야해)|니애미|느금"
                    + "|한남(?!동|대|산|역)|김치녀|틀딱|급식충|장애인 ?새끼|게이 ?새끼)",
            U);

    private final RuntimeSettings<DialogueProperties> props;
    private final CharacterContents contents;

    ChatScorer(RuntimeSettings<DialogueProperties> props, CharacterContents contents) {
        this.props = props;
        this.contents = contents;
    }

    /**
     * 앞에 걸리는 것이 이긴다: 심한 말 > 무례 > 고민 > 고마움 > 칭찬 > 불평 > 단답
     *
     * @param lowEffortMaxChars 공백을 빼고 이 글자 수 이하면 단답 (low-effort-max-chars)
     */
    static SignalType detect(String text, int lowEffortMaxChars) {
        String t = text == null ? "" : text.strip();
        if (t.isEmpty()) return SignalType.NEUTRAL;
        if (find(HATE_OR_SEXUAL, t)) return SignalType.HATE_OR_SEXUAL;
        if (find(RUDE, t)) return SignalType.RUDE;
        if (find(NEGATIVE, t)) return SignalType.CONFIDE;
        if (find(THANKS, t)) return SignalType.THANKS;
        if (find(COMPLIMENT, t)) return SignalType.COMPLIMENT;
        if (find(COMPLAINT, t)) return SignalType.COMPLAINT;
        if (t.replaceAll("\\s", "").length() <= lowEffortMaxChars || find(LOW_EFFORT, t)) return SignalType.LOW_EFFORT;
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

    private static boolean find(Pattern p, String s) {
        return p.matcher(s).find();
    }
}
