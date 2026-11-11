package com.capsule.user.client;

import org.springframework.web.util.HtmlUtils;

/**
 * 인증 메일 한 통의 제목 · 본문. HTML과 텍스트를 같이 보낸다.
 * 텍스트만 있으면 링크가 날것으로 보이고 스팸 점수도 오르기 쉽다. HTML을 못 여는 메일 앱은 텍스트를 보여준다.
 * HTML은 메일 앱 호환을 위해 표(table) 레이아웃 + 인라인 스타일만 쓴다(외부 CSS · 스크립트 없음).
 * 이미지는 머리의 캡슐 로고 하나(CDN 주소). 이미지를 막는 메일 앱에서는 대신 alt 글자 'CAPSULE'이 보인다.
 */
public record MailContent(String subject, String text, String html) {

    private static final String CODE_VALID = "5분";
    private static final String RESET_VALID = "30분";

    /**
     * 캡슐 로고(아이콘 + CAPSULE 글자, 276×64 PNG, 화면 138×32의 2배). Supabase 버킷 live2d-model.
     * 바꿀 때는 새 버전으로 올린다 (ADR 0024). v1 · v2(아이콘만)는 이미 보낸 메일이 쓰므로 지우지 않는다
     */
    public static final String ICON_PATH = "brand/capsule-icon-v3.png";

    private static final String BRAND_COLOR = "#6f52ed";

    /** 미리보기 문장 뒤에 본문 글자가 이어 붙어 보이지 않게 채우는 보이지 않는 공백 */
    private static final String PREHEADER_PAD = "&#847;&zwnj;&nbsp;".repeat(60);

    public static MailContent verificationCode(String code, String iconUrl) {
        String subject = "캡슐 이메일 인증번호 안내";
        String text = """
                안녕하세요, 캡슐이에요.

                회원가입을 마치려면 아래 인증번호를 가입 화면에 입력해 주세요.

                인증번호: %s
                (%s 동안 유효해요)

                캡슐에 가입하려던 게 아니라면 이 메일은 무시해 주세요.
                다른 분이 이메일 주소를 잘못 입력했을 수 있어요.

                - 캡슐 드림
                """.formatted(code, CODE_VALID);
        String block = """
                <div style="margin:28px 0;padding:20px 0;background:#f4f5f7;border-radius:10px;text-align:center;\
                font-size:32px;font-weight:700;letter-spacing:8px;color:#1f2329;">%s</div>
                <p style="margin:0;font-size:14px;color:#6b7280;">인증번호는 %s 동안 유효해요.</p>
                """.formatted(escape(code), CODE_VALID);
        String html = layout(
                iconUrl,
                subject,
                "인증번호 " + code + " · " + CODE_VALID + " 안에 입력해 주세요",
                "이메일 인증번호",
                "안녕하세요, 캡슐이에요.<br>회원가입을 마치려면 아래 인증번호를 가입 화면에 입력해 주세요.",
                block,
                "캡슐에 가입하려던 게 아니라면 이 메일은 무시해 주세요. 다른 분이 이메일 주소를 잘못 입력했을 수 있어요.");
        return new MailContent(subject, text, html);
    }

    public static MailContent passwordReset(String link, String iconUrl) {
        String subject = "캡슐 비밀번호 재설정 안내";
        String text = """
                안녕하세요, 캡슐이에요.

                비밀번호 재설정 요청을 받았어요.
                아래 링크를 열어 %s 안에 새 비밀번호를 설정해 주세요.

                %s

                링크는 한 번만 쓸 수 있어요.
                본인이 요청하지 않았다면 이 메일은 무시해 주세요. 비밀번호는 바뀌지 않아요.

                - 캡슐 드림
                """.formatted(RESET_VALID, link);
        String href = escape(link);
        String block = """
                <table role="presentation" cellpadding="0" cellspacing="0" style="margin:28px 0;">
                <tr><td style="border-radius:10px;background:%s;mso-padding-alt:14px 28px;">
                <a href="%s" target="_blank" style="display:inline-block;padding:14px 28px;font-size:16px;\
                font-weight:700;color:#ffffff;text-decoration:none;">새 비밀번호 설정하기</a>
                </td></tr></table>
                <p style="margin:0 0 8px;font-size:14px;color:#6b7280;">링크는 %s 동안, 한 번만 쓸 수 있어요.</p>
                <p style="margin:0;font-size:13px;color:#9ca3af;word-break:break-all;">버튼이 열리지 않으면 아래 주소를 \
                브라우저에 붙여 넣어 주세요.<br><a href="%s" target="_blank" style="color:#6b7280;">%s</a></p>
                """.formatted(BRAND_COLOR, href, RESET_VALID, href, href);
        String html = layout(
                iconUrl,
                subject,
                RESET_VALID + " 안에 새 비밀번호를 설정해 주세요",
                "비밀번호 재설정",
                "안녕하세요, 캡슐이에요.<br>비밀번호 재설정 요청을 받았어요. 아래 버튼을 눌러 새 비밀번호를 설정해 주세요.",
                block,
                "본인이 요청하지 않았다면 이 메일은 무시해 주세요. 비밀번호는 바뀌지 않아요.");
        return new MailContent(subject, text, html);
    }

    /** 공통 틀. preheader는 받은편지함 목록에서 제목 옆에 보이는 미리보기 문장 */
    private static String layout(
            String iconUrl, String title, String preheader, String heading, String intro, String block, String note) {
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background:#f4f5f7;">
                <div style="display:none;max-height:0;overflow:hidden;opacity:0;">%s%s</div>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f5f7;">
                <tr><td align="center" style="padding:40px 16px;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:480px;\
                background:#ffffff;border-radius:12px;">
                <tr><td style="padding:40px 32px;font-family:'Apple SD Gothic Neo','Malgun Gothic',\
                -apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;color:#1f2329;">
                <img src="%s" width="138" height="32" alt="CAPSULE" style="display:block;border:0;width:138px;\
                height:32px;margin:0 0 32px;font-size:18px;font-weight:800;color:#6f52ed;">
                <h1 style="margin:0 0 12px;font-size:22px;font-weight:700;">%s</h1>
                <p style="margin:0;font-size:15px;line-height:1.7;color:#374151;">%s</p>
                %s
                <hr style="margin:32px 0 20px;border:none;border-top:1px solid #e5e7eb;">
                <p style="margin:0;font-size:13px;line-height:1.6;color:#9ca3af;">%s</p>
                </td></tr></table>
                <p style="margin:20px 0 0;font-family:sans-serif;font-size:12px;color:#9ca3af;">\
                이 메일은 발신 전용이라 회신하셔도 받을 수 없어요.</p>
                </td></tr></table>
                </body>
                </html>
                """.formatted(
                        escape(title), escape(preheader), PREHEADER_PAD, escape(iconUrl), heading, intro, block, note);
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value);
    }
}
