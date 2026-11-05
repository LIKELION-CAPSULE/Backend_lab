package com.capsule.session.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공부 세션 설정. 자동 종료 배치 간격(auto-close-interval)은 @Scheduled가 직접 읽는다.
 *
 * @param autoCloseAfter heartbeat가 이보다 오래 없으면 자동 종료 (명세 21)
 * @param targetMinutesMin · targetMinutesMax · targetMinutesStep 목표 시간 범위와 단위 (명세 16)
 * @param targetMinutesExtra 범위 밖이어도 받는 목표 시간 (명세 16, 2026-11-05 최소 3분)
 * @param motionCooldown 같은 동작 이벤트가 이 안에 다시 오면 무시 (명세 18)
 */
@ConfigurationProperties("capsule.session")
public record SessionProperties(
        Duration autoCloseAfter,
        int targetMinutesMin,
        int targetMinutesMax,
        int targetMinutesStep,
        List<Integer> targetMinutesExtra,
        Duration motionCooldown) {}
