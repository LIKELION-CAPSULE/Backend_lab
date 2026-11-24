package com.capsule.dialogue.service;

import java.util.regex.Pattern;

/**
 * 음성으로 읽을 글 다듬기 (api.md 28 '음성' 읽기용 정리). 글(reply · done)은 그대로 두고 TTS에 넘길 조각만 바꾼다.
 * 소리로 읽으면 어색한 것을 뺀다: 지문 · 이모지 · 자음/모음만 있는 말 · 늘이는 물결 · 겹친 부호
 */
public final class SpeechText {
    private SpeechText() {}

    /** (한숨 쉬며) · [웃음] · *고개를 끄덕인다* — 행동 묘사 지문 */
    private static final Pattern STAGE = Pattern.compile("\\([^()]*\\)|（[^（）]*）|\\[[^\\[\\]]*]|\\*[^*]+\\*");
    /** 이모지 · 그림 기호 · 이모지 꾸밈 문자 (변형 선택자 · ZWJ · 피부색) */
    private static final Pattern EMOJI = Pattern.compile(
            "[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2B00}-\\x{2BFF}\\x{FE0E}\\x{FE0F}\\x{200D}\\x{20E3}]");
    /** ㅋㅋ · ㅎㅎ · ㅠㅠ · ㅇㅇ 처럼 자음 · 모음만 이어진 말 */
    private static final Pattern JAMO = Pattern.compile("[\\x{3131}-\\x{318E}]+");
    /** 꺾쇠 · 겹낫표: 글자만 남긴다 (「이거 어려워」 → 이거 어려워) */
    private static final Pattern BRACKETS = Pattern.compile("[「」『』<>《》〈〉]");
    /** 좋아~ · 좋아~~ → 좋아 */
    private static final Pattern TILDE = Pattern.compile("[~～]+");
    /** !!! → ! · ?? → ? · !?!? → !? */
    private static final Pattern MARKS = Pattern.compile("([!?])[!?]*");
    /** .... → ... · ……… → … (쉼으로 읽히게 하나만) */
    private static final Pattern DOTS = Pattern.compile("\\.{4,}|…{2,}");

    private static final Pattern SPACES = Pattern.compile("\\s+");

    /** 읽을 글. 다 빼고 소리낼 게 없으면 null (그 조각은 음성을 만들지 않는다) */
    public static String clean(String text) {
        if (text == null) return null;
        String s = STAGE.matcher(text).replaceAll(" ");
        s = EMOJI.matcher(s).replaceAll("");
        s = JAMO.matcher(s).replaceAll(" ");
        s = BRACKETS.matcher(s).replaceAll("");
        s = TILDE.matcher(s).replaceAll("");
        s = MARKS.matcher(s).replaceAll(m -> keepPair(m.group()));
        s = DOTS.matcher(s).replaceAll(m -> m.group().startsWith(".") ? "..." : "…");
        s = SPACES.matcher(s).replaceAll(" ").strip();
        // 앞에 남은 부호 (지문을 뺀 뒤 ". 왔어?" 같은 것)
        s = s.replaceFirst("^[\\s.,!?…]+", "");
        return s.codePoints().anyMatch(Character::isLetterOrDigit) ? s : null;
    }

    /** 겹친 물음 · 느낌표는 하나로, 둘 다 있으면 "?!" 순서 하나 */
    private static String keepPair(String marks) {
        boolean q = marks.indexOf('?') >= 0;
        boolean e = marks.indexOf('!') >= 0;
        return q && e ? "?!" : q ? "?" : "!";
    }
}
