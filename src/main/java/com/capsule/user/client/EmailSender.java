package com.capsule.user.client;

/** 인증 메일 발송 포트. 지금은 Fake만 있다. 실제 어댑터는 메일 발송사를 정한 뒤 (skeleton-plan 0장). */
public interface EmailSender {

    void sendVerificationCode(String email, String code);
}
