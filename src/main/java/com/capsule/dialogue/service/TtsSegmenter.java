package com.capsule.dialogue.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 흘러 들어오는 답장 글을 음성으로 바꿀 조각으로 자른다 (api.md 28 '음성', ADR 0034).
 * 문장 끝(. ! ? … ~ 줄바꿈)에서 자르고, 첫 조각만 쉼표에서도 자른다 (첫 소리를 빨리 내려고). 한 요청 안에서만 쓴다(스레드 하나)
 */
public class TtsSegmenter {

    private static final String SENTENCE_END = ".!?…~\n。";
    private static final String CLAUSE_END = ",，";

    private final int firstClauseMin;
    /** 이미 조각으로 넘긴 글자 수 */
    private int consumed;

    private boolean first = true;

    public TtsSegmenter(int firstClauseMin) {
        this.firstClauseMin = firstClauseMin;
    }

    /**
     * 지금까지의 글 전체(28 reply와 같은 값)를 받아, 새로 끝난 조각들을 돌려준다.
     * 앞부분이 바뀌었으면(정리된 글) 이미 넘긴 만큼은 건너뛴다
     */
    public List<String> feed(String soFar) {
        List<String> out = new ArrayList<>();
        if (soFar == null) return out;
        while (consumed < soFar.length()) {
            int end = boundary(soFar, consumed);
            if (end < 0) break;
            String piece = soFar.substring(consumed, end).strip();
            consumed = end;
            if (!speakable(piece)) continue;
            out.add(piece);
            first = false;
        }
        return out;
    }

    /** 답장이 끝났을 때: 아직 넘기지 않은 나머지를 문장 단위로 (대체 · 거절 대사처럼 한 번에 온 글도 나눠 첫 소리를 빨리) */
    public List<String> flush(String finalText) {
        List<String> out = new ArrayList<>();
        if (finalText == null) return out;
        out.addAll(feed(finalText));
        if (consumed < finalText.length()) {
            String rest = finalText.substring(consumed).strip();
            consumed = finalText.length();
            if (speakable(rest)) out.add(rest);
        }
        return out;
    }

    /** from 뒤 첫 끊는 자리(그 글자 다음 위치). 문장 끝 뒤에 붙은 닫는 따옴표 · 같은 부호는 같이 가져간다. 없으면 -1 */
    private int boundary(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean sentence = SENTENCE_END.indexOf(c) >= 0;
            boolean clause = first && CLAUSE_END.indexOf(c) >= 0 && i - from + 1 >= firstClauseMin;
            if (!sentence && !clause) continue;
            int end = i + 1;
            while (end < text.length()
                    && (SENTENCE_END.indexOf(text.charAt(end)) >= 0 || "\"'”’)」".indexOf(text.charAt(end)) >= 0)) end++;
            // 글이 아직 오는 중이면 "..." 처럼 부호가 이어질 수 있어 마지막 글자에서는 자르지 않는다
            if (end >= text.length()) return -1;
            return end;
        }
        return -1;
    }

    /** 부호 · 공백만이면 소리낼 게 없다 */
    private static boolean speakable(String piece) {
        return piece.codePoints().anyMatch(Character::isLetterOrDigit);
    }
}
