package com.capsule.user.client;

/** 인증 메일 발송 포트. 실제는 BrevoEmailSender(CAPSULE_BREVO_KEY가 있을 때), 없으면 local · test는 FakeEmailSender (ADR 0020). */
public interface EmailSender {

    void sendVerificationCode(String email, String code);

    /** 비밀번호 재설정 링크 (api.md 25). link에 재설정 토큰이 들어 있다 */
    void sendPasswordResetLink(String email, String link);
}
