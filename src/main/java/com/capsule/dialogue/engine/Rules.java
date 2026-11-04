package com.capsule.dialogue.engine;

import java.util.regex.Pattern;

/** 원본 JS 정규식의 Java 이식. JS의 /re/.test(s)는 Java의 find()와 같다. */
public final class Rules {
    private Rules() {}

    private static final int U = Pattern.UNICODE_CHARACTER_CLASS;

    public static final Pattern LOW_EFFORT = Pattern.compile(
            "^(ㅇㅇ+|ㅇ|응+|어+|ㄴㄴ|ㄴ|몰라|모름|ㅋ+|ㅎ+|ㅇㅋ|오키|ok|ㄱㄱ|넵|네|아니|그래|공부\\s*중|피곤|끝|ㅠ+|ㅜ+)[.!~?ㅋㅎ]*$",
            U | Pattern.CASE_INSENSITIVE);
    public static final Pattern DEFLECT = Pattern.compile("(안 속|몰라|별로네|관심 없|직접 찾아|알 바 아니)", U);
    public static final Pattern RHETORICAL = Pattern.compile("(설마[^?]*\\?|거야\\?|같아\\?|있었나\\?|어디\\?|래\\?|겠어\\?)", U);
    /** v5에서 '몇 문제 풀었는데?'를 재촉으로 오인하던 문제를 고친 버전. */
    public static final Pattern NAG =
            Pattern.compile("(공부(해|나|하자|부터|하라)|문제(나|부터)|문제 하나[는라도]* ?(더 )?풀|책 ?펴|빨리 ?펴|집중(해|하라)|펼쳐)", U);

    public static final Pattern QUESTION = Pattern.compile("\\?(?!!)", U);

    public static final Pattern ASKS_INFO = Pattern.compile("(설명|(?<!안\\s?)알려|가르쳐|원리|추세|방법|뭔지|어떻게 (해|하|되))", U);
    public static final Pattern ASKS_QUESTION = Pattern.compile("\\?|뭐야|뭐해|뭔데|왜|어떻게|알아|몇", U);
    public static final Pattern COMPLAINT = Pattern.compile("(재미없|노잼|잔소리|자꾸|그만 ?좀|엄마여|엄마야|짜증나|지겹|맨날)", U);
    public static final Pattern NEGATIVE = Pattern.compile("(힘들|우울|지쳐|지친|그만두|포기|죽겠|무서|불안)", U);
    public static final Pattern RUDE = Pattern.compile("(ㅗ|ㅅㅂ|씨발|시발|꺼져|닥쳐|미친|병신|ㅈ같)", U);
    public static final Pattern COMPLIMENT = Pattern.compile("(이쁘|예쁘|귀엽|멋있|멋지|잘한다|천재|대단)", U);
    /** 호감도 신호 THANKS (엔진 원본에는 없음, PM 2026-11-04) */
    public static final Pattern THANKS = Pattern.compile("(고마워|고맙|감사|땡큐|ㄱㅅ|ㄳ|thank)", U | Pattern.CASE_INSENSITIVE);
    /**
     * 호감도 신호 HATE_OR_SEXUAL 후보. 걸리면 결정 모델이 한 번 더 확인한다(오탐이면 RUDE).
     * ponytail: 짧은 단어 목록. 놓치는 말은 실험실 평가로 모아 늘린다
     */
    public static final Pattern HATE_OR_SEXUAL =
            Pattern.compile("(섹스|섹드립|야동|자위|따먹|가슴 ?(만지|보여)|벗어 ?봐|몸매 ?(좋|보여)|니애미|느금|애미|한남|김치녀|틀딱|급식충|장애인 ?새끼|게이 ?새끼)", U);

    public static final Pattern DISCLOSURE =
            Pattern.compile("(나 |내가|나는|오늘|요즘|방금).{0,20}(했|끝|망했|풀었|틀렸|힘들|무서|피곤|졸려|기뻐|좋아|싫어)", U);
    public static final Pattern REPEAT_WORD = Pattern.compile("(\\S{2,})(\\s+\\1){2,}", U);
    public static final Pattern BAIT = Pattern.compile("(왔어|죽었|찾던데|가져갔|스포|구라|거짓말|봤어|들었어|사귄대|잡혔)", U);

    public static final Pattern HMM = Pattern.compile("흥");
    public static final Pattern HMM_COMPLAINT = Pattern.compile("(흥이래|흥 좀|흥흥)");
    public static final Pattern PREF_NAG = Pattern.compile("(공부만|잔소리|엄마여|엄마야|공부하래|공부 하래)");
    public static final Pattern PREF_FUN = Pattern.compile("(재미없|노잼|지겹)");
    public static final Pattern PREF_NO_ASK = Pattern.compile("(안 ?알려|안 ?말해|말 안 ?해|비밀이)", U);

    public static final String PREF_HMM_TEXT = "\"흥\" 쓰지 않기";
    public static final String PREF_NAG_TEXT = "공부 재촉·잔소리 하지 않기";
    public static final String PREF_FUN_TEXT = "더 재밌게: 네 얘기·장난·사건으로 판 깔기";
    public static final String PREF_NO_ASK_TEXT = "사용자가 말하기 싫다고 한 건 다시 묻지 않기";

    public static boolean find(Pattern p, String s) {
        return s != null && p.matcher(s).find();
    }

    public static boolean hasQ(String s) {
        return find(QUESTION, s);
    }

    public static String pct(double x) {
        return Math.round(x * 100) + "%";
    }
}
