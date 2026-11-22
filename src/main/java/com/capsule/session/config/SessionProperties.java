package com.capsule.session.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 연결 설정. 자동 종료 배치 간격(auto-close-interval)은 @Scheduled가 직접 읽는다.
 * 목표 시간(target-minutes-*)은 2026-11-23 목표 시간이 없어져 뺐다 (명세 16)
 *
 * @param autoCloseAfter heartbeat가 이보다 오래 없으면 자동 종료 (명세 21)
 * @param motionCooldown 같은 동작 이벤트가 이 안에 다시 오면 무시 (명세 18)
 */
@ConfigurationProperties("capsule.session")
public record SessionProperties(Duration autoCloseAfter, Duration motionCooldown) {}
