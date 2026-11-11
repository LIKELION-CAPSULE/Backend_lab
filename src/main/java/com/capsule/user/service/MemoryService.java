package com.capsule.user.service;

import com.capsule.common.error.ApiException;
import com.capsule.common.time.ClockConfig;
import com.capsule.user.api.UserTypes.MemoryInfo;
import com.capsule.user.domain.User;
import com.capsule.user.domain.UserMemory;
import com.capsule.user.domain.UserMemory.Type;
import com.capsule.user.dto.CreateMemoryRequest;
import com.capsule.user.dto.MemoryList;
import com.capsule.user.dto.MemoryResponse;
import com.capsule.user.dto.MemoryStatus;
import com.capsule.user.repository.UserMemoryRepository;
import com.capsule.user.repository.UserRepository;
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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일정 저장·조회 (api.md 13, 14). 날짜는 한국 기준. MemoryQuery 계약은
 * {@link com.capsule.user.adapter.MemoryQueryAdapter}가 이 클래스로 위임
 */
@Service
@RequiredArgsConstructor
public class MemoryService {

    private static final String DEFAULT_PROMISE_TITLE = "같이 공부하기";
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_RANGE_DAYS = 100;

    /** 같은 날이면 시간 있는 일정 먼저, 종일은 그 다음 */
    private static final Comparator<UserMemory> SAME_DAY = Comparator.comparing(
                    UserMemory::getDueTime, Comparator.nullsLast(Comparator.<LocalTime>naturalOrder()))
            .thenComparing(UserMemory::getId);
    /** 예정·전체: 가까운 날짜부터, 날짜 없는 목표는 맨 아래 */
    private static final Comparator<UserMemory> NEAREST_FIRST = Comparator.comparing(
                    UserMemory::getDueDate, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
            .thenComparing(SAME_DAY);
    /** 지난 일정: 최근 것부터 */
    private static final Comparator<UserMemory> LATEST_FIRST = Comparator.comparing(
                    UserMemory::getDueDate, Comparator.nullsLast(Comparator.<LocalDate>reverseOrder()))
            .thenComparing(SAME_DAY);

    private final UserMemoryRepository memories;
    private final UserRepository users;
    private final Clock clock;

    /** EXAM·PROMISE는 날짜 필수, GOAL은 선택(B9). 날짜 없이 시간만·지난 날짜는 안 됨 */
    @Transactional
    public MemoryResponse create(long userId, CreateMemoryRequest req) {
        // 탈퇴(api.md 27) 뒤 남은 액세스 토큰으로 일정이 다시 생기지 않게
        if (users.findById(userId).filter(User::isActive).isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }
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
        if (type != Type.PROMISE) {
            dueTime = null; // 시간은 약속에서만 쓴다 (api.md 13). 형식 검사는 위에서 이미 함
        }
        return toResponse(memories.save(new UserMemory(userId, type, title, dueDate, dueTime)), today);
    }

    /** date가 있으면 from·to보다 우선. datesWithMemories는 from~to 안의 날짜(탭·date와 무관) */
    @Transactional(readOnly = true)
    public MemoryList list(long userId, LocalDate from, LocalDate to, LocalDate date, MemoryStatus status, Type type) {
        if (from != null && to != null && (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE");
        }
        LocalDate today = today();
        // ponytail: 사용자 일정을 전부 읽어 메모리에서 거른다. 일정이 수천 개가 되면 SQL 조건으로
        List<UserMemory> mine = memories.findByUserId(userId).stream()
                .filter(m -> type == null || m.getType() == type)
                .toList();
        List<MemoryResponse> listed = mine.stream()
                // 날짜 없는 목표는 기간과 상관없이 넣고, status에서 '전체'만 남긴다 (api.md 14 규칙)
                .filter(m -> date != null
                        ? date.equals(m.getDueDate())
                        : m.getDueDate() == null || inRange(m.getDueDate(), from, to))
                .filter(m -> inStatus(status, m.getDueDate(), today))
                .sorted(status == MemoryStatus.PAST ? LATEST_FIRST : NEAREST_FIRST)
                .map(m -> toResponse(m, today))
                .toList();
        List<LocalDate> dates = mine.stream()
                .map(UserMemory::getDueDate)
                .filter(d -> d != null && inRange(d, from, to))
                .distinct()
                .sorted()
                .toList();
        return new MemoryList(listed, dates);
    }

    @Transactional(readOnly = true)
    public List<MemoryInfo> memoriesForConversation(long userId) {
        LocalDate today = today();
        return memories.findByUserId(userId).stream()
                .filter(m -> m.getDueDate() == null || !m.getDueDate().isBefore(today))
                .sorted(NEAREST_FIRST)
                .map(m -> new MemoryInfo(m.getId(), m.getType().name(), m.getTitle(), m.getDueDate(), m.getDueTime()))
                .toList();
    }

    private LocalDate today() {
        return LocalDate.ofInstant(Instant.now(clock), ClockConfig.KST);
    }

    private static MemoryResponse toResponse(UserMemory m, LocalDate today) {
        return new MemoryResponse(
                m.getId(),
                m.getType(),
                m.getTitle(),
                m.getDueDate(),
                m.getDueTime() == null ? null : m.getDueTime().format(HH_MM),
                m.getDueDate() == null ? null : (int) ChronoUnit.DAYS.between(today, m.getDueDate()));
    }

    /** 범위가 없으면 날짜 없는 목표도 포함 */
    private static boolean inRange(LocalDate d, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return true;
        }
        return d != null && (from == null || !d.isBefore(from)) && (to == null || !d.isAfter(to));
    }

    private static boolean inStatus(MemoryStatus status, LocalDate d, LocalDate today) {
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
