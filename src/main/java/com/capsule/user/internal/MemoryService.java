package com.capsule.user.internal;

import com.capsule.common.error.ApiException;
import com.capsule.common.time.ClockConfig;
import com.capsule.user.api.MemoryQuery;
import com.capsule.user.api.UserTypes.MemoryInfo;
import com.capsule.user.internal.UserMemory.Type;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 일정 저장·조회 (api.md 13, 14) + MemoryQuery 계약. 날짜는 한국 기준 */
@Service
class MemoryService implements MemoryQuery {

    private static final String DEFAULT_PROMISE_TITLE = "같이 공부하기";
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_RANGE_DAYS = 100;

    /** 같은 날이면 시간 있는 일정 먼저, 종일은 그 다음 */
    private static final Comparator<UserMemory> SAME_DAY = Comparator.comparing(
                    UserMemory::dueTime, Comparator.nullsLast(Comparator.<LocalTime>naturalOrder()))
            .thenComparing(UserMemory::id);
    /** 예정·전체: 가까운 날짜부터, 날짜 없는 목표는 맨 아래 */
    private static final Comparator<UserMemory> NEAREST_FIRST = Comparator.comparing(
                    UserMemory::dueDate, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
            .thenComparing(SAME_DAY);
    /** 지난 일정: 최근 것부터 */
    private static final Comparator<UserMemory> LATEST_FIRST = Comparator.comparing(
                    UserMemory::dueDate, Comparator.nullsLast(Comparator.<LocalDate>reverseOrder()))
            .thenComparing(SAME_DAY);

    enum Status {
        UPCOMING,
        ALL,
        PAST
    }

    /** 형식 오류도 INVALID_MEMORY로 내려고 문자열로 받는다 */
    record CreateMemoryRequest(String type, String title, String dueDate, String dueTime) {}

    record MemoryResponse(long memoryId, Type type, String title, LocalDate dueDate, String dueTime, Integer dDay) {}

    record MemoryList(List<MemoryResponse> memories, List<LocalDate> datesWithMemories) {}

    private final UserMemoryRepository memories;
    private final Clock clock;

    MemoryService(UserMemoryRepository memories, Clock clock) {
        this.memories = memories;
        this.clock = clock;
    }

    /** EXAM·PROMISE는 날짜 필수, GOAL은 선택(B9). 날짜 없이 시간만·지난 날짜는 안 됨 */
    @Transactional
    MemoryResponse create(long userId, CreateMemoryRequest req) {
        Type type = parse(req.type(), Type::valueOf);
        LocalDate dueDate = parse(req.dueDate(), LocalDate::parse);
        LocalTime dueTime = parse(req.dueTime(), s -> LocalTime.parse(s, HH_MM));
        String title = req.title() == null ? "" : req.title().strip();
        if (title.isEmpty() && type == Type.PROMISE) {
            title = DEFAULT_PROMISE_TITLE;
        }
        LocalDate today = today();
        if (type == null
                || title.isEmpty()
                || (dueDate == null && (type != Type.GOAL || dueTime != null))
                || (dueDate != null && dueDate.isBefore(today))) {
            throw invalidMemory();
        }
        return toResponse(memories.save(new UserMemory(userId, type, title, dueDate, dueTime)), today);
    }

    /** date가 있으면 from·to보다 우선. datesWithMemories는 from~to 안의 날짜(탭·date와 무관) */
    @Transactional(readOnly = true)
    MemoryList list(long userId, LocalDate from, LocalDate to, LocalDate date, Status status, Type type) {
        if (from != null && to != null && (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE");
        }
        LocalDate today = today();
        // ponytail: 사용자 일정을 전부 읽어 메모리에서 거른다. 일정이 수천 개가 되면 SQL 조건으로
        List<UserMemory> mine = memories.findByUserId(userId).stream()
                .filter(m -> type == null || m.type() == type)
                .toList();
        List<MemoryResponse> listed = mine.stream()
                .filter(m -> date != null ? date.equals(m.dueDate()) : inRange(m.dueDate(), from, to))
                .filter(m -> inStatus(status, m.dueDate(), today))
                .sorted(status == Status.PAST ? LATEST_FIRST : NEAREST_FIRST)
                .map(m -> toResponse(m, today))
                .toList();
        List<LocalDate> dates = mine.stream()
                .map(UserMemory::dueDate)
                .filter(d -> d != null && inRange(d, from, to))
                .distinct()
                .sorted()
                .toList();
        return new MemoryList(listed, dates);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemoryInfo> memoriesForConversation(long userId) {
        LocalDate today = today();
        return memories.findByUserId(userId).stream()
                .filter(m -> m.dueDate() == null || !m.dueDate().isBefore(today))
                .sorted(NEAREST_FIRST)
                .map(m -> new MemoryInfo(m.id(), m.type().name(), m.title(), m.dueDate(), m.dueTime()))
                .toList();
    }

    private LocalDate today() {
        return LocalDate.ofInstant(Instant.now(clock), ClockConfig.KST);
    }

    private static MemoryResponse toResponse(UserMemory m, LocalDate today) {
        return new MemoryResponse(
                m.id(),
                m.type(),
                m.title(),
                m.dueDate(),
                m.dueTime() == null ? null : m.dueTime().format(HH_MM),
                m.dueDate() == null ? null : (int) ChronoUnit.DAYS.between(today, m.dueDate()));
    }

    /** 범위가 없으면 날짜 없는 목표도 포함 */
    private static boolean inRange(LocalDate d, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return true;
        }
        return d != null && (from == null || !d.isBefore(from)) && (to == null || !d.isAfter(to));
    }

    private static boolean inStatus(Status status, LocalDate d, LocalDate today) {
        return switch (status) {
            case ALL -> true;
            case UPCOMING -> d != null && !d.isBefore(today);
            case PAST -> d != null && d.isBefore(today);
        };
    }

    private static <T> T parse(String value, Function<String, T> parser) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return parser.apply(value.strip());
        } catch (IllegalArgumentException | DateTimeException e) {
            throw invalidMemory();
        }
    }

    private static ApiException invalidMemory() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MEMORY");
    }
}
