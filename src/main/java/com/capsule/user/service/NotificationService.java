package com.capsule.user.service;

import com.capsule.common.error.ApiException;
import com.capsule.common.error.CommonErrorCode;
import com.capsule.credit.api.AttendanceGrantedEvent;
import com.capsule.user.domain.Notification;
import com.capsule.user.dto.NotificationList;
import com.capsule.user.repository.NotificationRepository;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 (api.md 43 · 44, ADR 0035). 다른 모듈의 일을 받아 문구를 만들어 적고, 목록 · 읽음을 준다.
 * 문구는 여기서만 만든다 (조사 '과/와' · '이/가'를 이름 받침에 맞춤)
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;
    /** 이보다 오래된 알림은 목록 · 안 읽은 수에서 뺀다 (명세 43) */
    static final Duration KEEP = Duration.ofDays(90);

    private static final String MATES_ME = "/mates/me";
    private static final String CREDITS = "/credits";
    private static final String INVITE = "/credits/invite";

    private final NotificationRepository notifications;
    private final Clock clock;

    // ---- 적기

    @Transactional
    public void stageUp(
            long userId, long connectionId, String mateName, int stage, String stageName, boolean unlocked) {
        String reached = stageName == null || stageName.isBlank() ? stage + "단계" : "\"" + stageName + "\"";
        String subject = stageName == null || stageName.isBlank() ? stage + "단계" : stageName;
        String title = mateName + josa(mateName, "과", "와") + " " + reached + josa(subject, "이", "가") + " 됐어요"
                + (unlocked ? " · 새 표정" : "");
        notifications.insert(userId, Notification.STAGE_UP, title, null, MATES_ME, "connection:" + connectionId, now());
    }

    /** credit이 출석을 지급하면 같은 트랜잭션에서 (그날 한 번) */
    @EventListener
    @Transactional
    public void onAttendance(AttendanceGrantedEvent e) {
        notifications.insert(
                e.userId(),
                Notification.ATTENDANCE,
                "오늘 출석 크레딧 " + won(e.amount()),
                null,
                CREDITS,
                "day:" + e.day(),
                now());
    }

    /**
     * 초대 보상 (36 · 22). 초대한 사람: "민지님 첫 연결 · 크레딧 1,000", 받은 사람: "친구 초대 보상 · 크레딧 1,000"
     *
     * @param inviteeNickname 초대받은 사람 닉네임 (초대한 사람 몫일 때)
     */
    @Transactional
    public void referralReward(long userId, long referralId, boolean inviter, String inviteeNickname, int amount) {
        String title = inviter
                ? (inviteeNickname == null ? "친구" : inviteeNickname + "님") + " 첫 연결 · 크레딧 " + won(amount)
                : "친구 초대 보상 · 크레딧 " + won(amount);
        notifications.insert(
                userId,
                Notification.REFERRAL_REWARD,
                title,
                null,
                INVITE,
                "referral:" + referralId + (inviter ? ":inviter" : ":invitee"),
                now());
    }

    // ---- 43 · 44

    @Transactional(readOnly = true)
    public NotificationList list(long userId, Long before, Integer size) {
        int n = size == null ? DEFAULT_SIZE : size;
        if (n < 1 || n > MAX_SIZE || (before != null && before < 1)) {
            throw new ApiException(CommonErrorCode.INVALID_REQUEST);
        }
        Instant since = now().minus(KEEP);
        List<Notification> rows = notifications.findPage(
                userId, before == null ? Long.MAX_VALUE : before, since, PageRequest.of(0, n + 1));
        boolean more = rows.size() > n;
        List<Notification> shown = more ? rows.subList(0, n) : rows;
        return new NotificationList(
                shown.stream()
                        .map(x -> new NotificationList.Item(
                                x.getId(),
                                x.getType(),
                                x.getTitle(),
                                x.getBody(),
                                x.getLink(),
                                x.getReadAt() != null,
                                x.getCreatedAt()))
                        .toList(),
                notifications.countUnread(userId, since),
                more ? shown.getLast().getId() : null);
    }

    @Transactional
    public NotificationList.Unread read(long userId, List<Long> ids) {
        if (ids == null) notifications.markAllRead(userId, now());
        else if (!ids.isEmpty()) notifications.markRead(userId, ids, now());
        return new NotificationList.Unread(unreadCount(userId));
    }

    /** 8 unreadNotifications */
    @Transactional(readOnly = true)
    public int unreadCount(long userId) {
        return notifications.countUnread(userId, now().minus(KEEP));
    }

    // ---- 도우미

    /** 마지막 글자 받침이 있으면 withFinal ('과' · '이'), 없거나 한글이 아니면 withoutFinal ('와' · '가') */
    static String josa(String word, String withFinal, String withoutFinal) {
        if (word == null || word.isEmpty()) return withoutFinal;
        char last = word.charAt(word.length() - 1);
        if (last < 0xAC00 || last > 0xD7A3) return withoutFinal;
        return (last - 0xAC00) % 28 == 0 ? withoutFinal : withFinal;
    }

    private static String won(int amount) {
        return NumberFormat.getIntegerInstance(Locale.KOREA).format(amount);
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
