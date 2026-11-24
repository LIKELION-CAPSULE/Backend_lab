package com.capsule.user.service;

import com.capsule.common.time.ClockConfig;
import com.capsule.user.api.UserTypes.MemoryInfo;
import com.capsule.user.domain.UserMemory;
import com.capsule.user.repository.UserMemoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일정 읽기. 저장 · 조회 API(api.md 13 · 14)는 2026-11-24에 지웠다 (12/1 베타 화면에 없음).
 * 이미 저장된 일정은 대화 프롬프트가 계속 읽는다: MemoryQuery 계약은
 * {@link com.capsule.user.adapter.MemoryQueryAdapter}가 이 클래스로 위임
 */
@Service
@RequiredArgsConstructor
public class MemoryService {

    /** 같은 날이면 시간 있는 일정 먼저, 종일은 그 다음 */
    private static final Comparator<UserMemory> SAME_DAY = Comparator.comparing(
                    UserMemory::getDueTime, Comparator.nullsLast(Comparator.<LocalTime>naturalOrder()))
            .thenComparing(UserMemory::getId);
    /** 가까운 날짜부터, 날짜 없는 목표는 맨 아래 */
    private static final Comparator<UserMemory> NEAREST_FIRST = Comparator.comparing(
                    UserMemory::getDueDate, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
            .thenComparing(SAME_DAY);

    private final UserMemoryRepository memories;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<MemoryInfo> memoriesForConversation(long userId) {
        LocalDate today = LocalDate.ofInstant(Instant.now(clock), ClockConfig.KST);
        return memories.findByUserId(userId).stream()
                .filter(m -> m.getDueDate() == null || !m.getDueDate().isBefore(today))
                .sorted(NEAREST_FIRST)
                .map(m -> new MemoryInfo(m.getId(), m.getType().name(), m.getTitle(), m.getDueDate(), m.getDueTime()))
                .toList();
    }
}
