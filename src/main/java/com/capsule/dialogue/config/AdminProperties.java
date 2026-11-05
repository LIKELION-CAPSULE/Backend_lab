package com.capsule.dialogue.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 실험실(운영 설정 포함) 관리자 계정 (ADR 0027). 값은 환경변수로만 넣는다: CAPSULE_ADMIN_USERS_&lt;아이디&gt;=&lt;비밀번호&gt;
 * 비밀번호는 Spring 저장 형식: {bcrypt}$2a$10$… (권장, Railway 변수가 새도 비밀번호는 안 샘) 또는 {noop}평문.
 * 아이디는 영문 소문자 · 숫자만: 환경변수 이름은 소문자로 바뀌고 밑줄(_)은 점으로 읽힌다 (CAPSULE_ADMIN_USERS_SEONGUK → 아이디 seonguk).
 *
 * @param users 아이디 → 비밀번호. 비면 관리자 로그인이 없다 (로컬: 이 컴퓨터에서만, 또는 capsule.llm.lab.token)
 * @param sessionTtl 로그인 한 번이 유지되는 시간
 */
@ConfigurationProperties("capsule.admin")
public record AdminProperties(Map<String, String> users, Duration sessionTtl) {

    public AdminProperties {
        users = users == null ? Map.of() : Map.copyOf(users);
        if (sessionTtl == null) sessionTtl = Duration.ofHours(12);
    }
}
