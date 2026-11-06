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
    /** 상대를 부르는 말 한 단어 ('너무' · '아니'의 너 · 니는 아님) */
    private static final String YOU = "(?<![가-힣])(너|넌|너는|너도|너가|니가|네가|너의|니|널|너를)(?![가-힣])";
    /**
     * 애정 표현 (2026-11-07). '보고 싶다'는 '영화 보고 싶다'와 헷갈려 '보고 싶었'만.
     * ponytail: 짧은 단어 목록. 놓치는 말은 평범한 말(0점)로 빠진다. 많으면 결정 모델(signal-detector: DECIDER)로
     */
    private static final Pattern AFFECTION = Pattern.compile(
            YOU + "\\s*(진짜|정말|완전|많이|제일|너무|좀)?\\s*(좋아(해|한다|하는|졌)|사랑)"
                    + "|^(좋아해|사랑해)|보고 ?싶었|그리웠"
                    + "|(?<![가-힣])(너|네)\\s*(랑|하고|와|옆에서|있어서|있으니까)[^.!?\\n]{0,15}(?<!안 ?)(좋|행복|든든|즐거|재밌|설레)",
            U);
    /** 놀림 · 깎아내림 (2026-11-07). '나 바보인가'처럼 자기를 낮추는 말과 헷갈리지 않게 상대를 부르는 말 뒤에서만 */
    private static final Pattern TEASE =
            Pattern.compile(YOU + "\\s*(진짜|정말|완전|좀|되게|너무|존나)?\\s*(찌질|한심|바보|멍청|못생|재수 ?없|허접|구려|구리다|하찮|겁쟁이|비겁)", U);
    /** 공부 성과 · 근황 공유 (2026-11-07). 물음표로 끝나면 묻는 말이라 보지 않는다 (detect) */
    private static final Pattern SHARE = Pattern.compile(
            "(다 ?(풀|했|끝냈|외웠)|풀었|끝냈|외웠|해냈|합격|붙었|만점|백점|1등|일등|시험 ?(잘|봤|끝났)|(점수|성적) ?(올랐|잘)"
                    + "|(\\d+|한|두|세|네|다섯)\\s*시간\\s*(째|동안|이나|했|공부))",
            U);
    /** 캐릭터 자신에 대해 묻는 말 (2026-11-07): 상대를 부르는 말이 든 물음, 또는 '궁금' */
    private static final Pattern CURIOUS = Pattern.compile(YOU + "[^?]*\\?|궁금", U);
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
     * 앞에 걸리는 것이 이긴다: 심한 말 > 무례 > 놀림 > 고민 > 고마움 > 애정 > 칭찬 > 불평 > 근황 > 관심 > 단답.
     * 불평이 근황 · 관심보다 앞: '너 왜 맨날 잔소리야?'는 관심(+1)이 아니라 불평
     *
     * @param lowEffortMaxChars 공백을 빼고 이 글자 수 이하면 단답 (low-effort-max-chars)
     */
    static SignalType detect(String text, int lowEffortMaxChars) {
        String t = text == null ? "" : text.strip();
        if (t.isEmpty()) return SignalType.NEUTRAL;
        if (find(HATE_OR_SEXUAL, t)) return SignalType.HATE_OR_SEXUAL;
        if (find(RUDE, t)) return SignalType.RUDE;
        if (find(TEASE, t)) return SignalType.TEASE;
        if (find(NEGATIVE, t)) return SignalType.CONFIDE;
        if (find(THANKS, t)) return SignalType.THANKS;
        if (find(AFFECTION, t)) return SignalType.AFFECTION;
        if (find(COMPLIMENT, t)) return SignalType.COMPLIMENT;
        if (find(COMPLAINT, t)) return SignalType.COMPLAINT;
        if (!t.endsWith("?") && find(SHARE, t)) return SignalType.SHARE;
        if (find(CURIOUS, t)) return SignalType.CURIOUS;
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
