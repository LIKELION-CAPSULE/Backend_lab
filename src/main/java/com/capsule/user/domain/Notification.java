package com.capsule.user.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 알림 한 줄 (notifications, api.md 43 · 44, ADR 0035). 쓰기는 NotificationRepository.insert(중복이면 무시)로만 */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    public static final String STAGE_UP = "STAGE_UP";
    public static final String ATTENDANCE = "ATTENDANCE";
    public static final String REFERRAL_REWARD = "REFERRAL_REWARD";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long userId;
    private String type;
    private String title;
    private String body;
    private String link;
    private String dedupKey;
    private Instant createdAt;
    private Instant readAt;
}
