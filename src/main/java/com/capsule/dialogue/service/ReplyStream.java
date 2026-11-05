package com.capsule.dialogue.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 스트리밍 중인 답장 원문에서 지금까지의 대사만 꺼낸다 (명세 28 `reply` 이벤트).
 * 답장 형식은 "대사 [emotion=감정]"(scene.system), 옛 형식 {"reply": "대사", "emotion": "감정"}도 받는다.
 * 최종 답장은 끝난 뒤 plain · replyJson이 정한다: 여기 값은 미리보기일 뿐이다.
 */
final class ReplyStream {

    /** 대사 뒤에 감정 표시가 시작되는 곳: {"…, [emotion · (emotion, emotion= · emotion: */
    private static final Pattern TAIL =
            Pattern.compile("\\{\"|[\\[(]\\s*emotion|\\bemotion\\s*[=:]", Pattern.CASE_INSENSITIVE);

    /** 맨 앞에 먼저 쓴 태그 [emotion=…] */
    private static final Pattern LEADING_TAG =
            Pattern.compile("\\[\\s*emotion\\s*[=:][^\\]\\n]*\\]\\s*", Pattern.CASE_INSENSITIVE);

    private ReplyStream() {}

    /** 지금까지의 대사. 아직 대사가 시작되지 않았으면 null */
    static String partial(String raw) {
        if (raw == null) return null;
        String t = raw.strip();
        if (t.startsWith("```")) {
            int brace = t.indexOf('{');
            if (brace < 0) return null; // 코드 울타리만 왔다
            t = t.substring(brace);
        }
        if (t.isEmpty() || t.equals("{")) return null; // JSON인지 아직 모른다
        if (!t.startsWith("{\"")) {
            // JSON 없이 글만 쓰는 답(기본 형식, 끝에 [emotion=…]). 맨 앞에 태그를 먼저 쓴 답은 태그를 건너뛴다
            Matcher lead = LEADING_TAG.matcher(t);
            if (lead.lookingAt()) t = t.substring(lead.end());
            else if (t.startsWith("[")
                    && "[emotion"
                            .startsWith(t.toLowerCase(java.util.Locale.ROOT).substring(0, Math.min(t.length(), 8))))
                return null; // 맨 앞 태그가 아직 덜 왔다
            // 태그 · 감정 표시({"emotion": …}, emotion=…)가 시작되면 그 앞까지. 대사 속 낱말 emotion은 자르지 않는다
            Matcher tailAt = TAIL.matcher(t);
            int tail = tailAt.find() ? tailAt.start() : -1;
            String text = (tail < 0 ? t : t.substring(0, tail)).strip();
            // 끝 태그 [emotion=…]가 반쯤 온 것("[", "[em")도 보이지 않게
            int open = Math.max(text.lastIndexOf('['), text.lastIndexOf('('));
            if (open >= 0
                    && "emotion".startsWith(text.substring(open + 1).strip().toLowerCase(java.util.Locale.ROOT)))
                text = text.substring(0, open).strip();
            return text.isEmpty() ? null : text;
        }
        int key = t.indexOf("\"reply\"");
        if (key < 0) return null;
        int i = key + "\"reply\"".length();
        while (i < t.length() && Character.isWhitespace(t.charAt(i))) i++;
        if (i >= t.length() || t.charAt(i) != ':') return null;
        i++;
        while (i < t.length() && Character.isWhitespace(t.charAt(i))) i++;
        if (i >= t.length() || t.charAt(i) != '"') return null;
        return unescape(t, i + 1);
    }

    /** from부터 닫는 따옴표(또는 끝)까지의 JSON 문자열. 끝에 걸친 이스케이프는 다음 조각을 기다린다 */
    private static String unescape(String t, int from) {
        StringBuilder out = new StringBuilder();
        for (int i = from; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '"') break;
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (i + 1 >= t.length()) break;
            char e = t.charAt(++i);
            switch (e) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'b', 'f' -> {}
                case 'u' -> {
                    if (i + 4 >= t.length()) return out.toString();
                    try {
                        out.append((char) Integer.parseInt(t.substring(i + 1, i + 5), 16));
                    } catch (NumberFormatException ex) {
                        return out.toString();
                    }
                    i += 4;
                }
                default -> out.append(e); // \" \\ \/
            }
        }
        return out.toString();
    }
}
