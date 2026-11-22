package com.capsule.dialogue.service;

import java.util.Optional;
import org.springframework.stereotype.Component;

/** 기본 판정: 아무것도 잡지 않는다 (api.md 20 '안전': 탐지 기준 확정 전까지 자리만) */
// TODO(judgment): J-17 ① 무엇을 위반으로 볼지(탐지 기준 · 종류 이름 · 판정 방법). 정해지면 이 구현을 바꾼다. 답변 쪽 검사도 기준이 정해지면
@Component
public class PassingSafetyChecker implements SafetyChecker {

    @Override
    public Optional<String> violation(String userMessage) {
        return Optional.empty();
    }
}
