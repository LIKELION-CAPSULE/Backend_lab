package com.capsule.user.dto;

import java.time.Instant;
import java.util.List;

/** api.md 43 알림 목록. nextBefore = 다음 페이지를 부를 before, 끝이면 null */
public record NotificationList(List<Item> items, int unreadCount, Long nextBefore) {

    public record Item(long id, String type, String title, String body, String link, boolean read, Instant createdAt) {}

    /** api.md 44 응답 */
    public record Unread(int unreadCount) {}

    /** api.md 44 요청. ids가 없으면 전부 */
    public record ReadRequest(List<Long> ids) {}
}
