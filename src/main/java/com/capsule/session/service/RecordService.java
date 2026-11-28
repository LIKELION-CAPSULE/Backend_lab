package com.capsule.session.service;

import com.capsule.common.error.ApiException;
import com.capsule.common.error.CommonErrorCode;
import com.capsule.content.api.ContentTypes.MateInfo;
import com.capsule.content.api.ContentTypes.UnlockInfo;
import com.capsule.content.api.MateQuery;
import com.capsule.content.api.UnlockQuery;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.api.RelationTypes.IntimacyNow;
import com.capsule.session.domain.StudySession;
import com.capsule.session.dto.MyMatesResponse;
import com.capsule.session.dto.RecordsResponse;
import com.capsule.session.repository.MateTotals;
import com.capsule.session.repository.StudySessionRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 메이트 (api.md 37) · 내 기록 (api.md 38). 끝난 연결(connections)만 모은다 */
@Service
@RequiredArgsConstructor
public class RecordService {

    private static final int RECENT_SIZE = 5;
    private static final Set<String> RANGES = Set.of("WEEK", "MONTH", "YEAR");

    private final StudySessionRepository sessions;
    private final MateQuery mates;
    private final UnlockQuery unlocks;
    private final RelationQuery relations;
    private final Clock clock;

    // ---- 37 내 메이트

    @Transactional(readOnly = true)
    public MyMatesResponse myMates(long userId) {
        List<MateTotals> totals = sessions.mateTotals(userId);
        if (totals.isEmpty()) {
            return new MyMatesResponse(List.of());
        }
        Set<Long> unlocked = relations.unlockedContentIds(userId);
        List<MyMatesResponse.MyMate> list = new ArrayList<>();
        for (MateTotals t : totals) {
            long mateId = t.getMateId();
            Optional<MateInfo> found = mates.find(mateId);
            if (found.isEmpty()) continue; // 캐릭터 행은 FK로 남아 있어 실제로는 없음
            MateInfo mate = found.get();
            list.add(new MyMatesResponse.MyMate(
                    mate.id(),
                    mate.name(),
                    mate.cardImageUrl(),
                    t.getFirstMetAt(),
                    t.getLastConnectedAt(),
                    t.getConnectionCount().intValue(),
                    t.getPresentSeconds().intValue(),
                    t.getConnectedSeconds().intValue(),
                    relationOf(mate, relations.intimacyNow(userId, mateId)),
                    unlocksOf(mate, unlocked),
                    sessions.recentEnded(userId, mateId).stream()
                            .map(s -> new MyMatesResponse.Recent(
                                    s.getId(),
                                    s.getStartedAt(),
                                    s.getEndedAt(),
                                    s.getPresentSeconds(),
                                    s.getConnectedSeconds()))
                            .toList()));
        }
        return new MyMatesResponse(list);
    }

    private static MyMatesResponse.Relation relationOf(MateInfo mate, IntimacyNow now) {
        boolean last = now.nextStageSeconds() == null;
        return new MyMatesResponse.Relation(
                now.stage(),
                SessionService.stageName(mate, now.stage()),
                last ? null : SessionService.stageName(mate, now.stage() + 1),
                now.nextStageSeconds(),
                now.stageProgress());
    }

    /** 이 메이트의 해금 콘텐츠 전부 (열리는 단계 순). 열렸는지는 relation의 해금 기록으로 */
    private List<MyMatesResponse.Unlock> unlocksOf(MateInfo mate, Set<Long> unlocked) {
        List<UnlockInfo> all = unlocks.between(mate.id(), 0, Integer.MAX_VALUE);
        return all.stream()
                .map(u -> new MyMatesResponse.Unlock(
                        u.id(),
                        u.type(),
                        u.title(),
                        u.unlockStage(),
                        SessionService.stageName(mate, u.unlockStage()),
                        u.expressionName(),
                        u.assetUrl(),
                        unlocked.contains(u.id())))
                .toList();
    }

    // ---- 38 내 기록

    /** range · date가 없거나 형식이 틀리면 400 INVALID_REQUEST */
    @Transactional(readOnly = true)
    public RecordsResponse records(long userId, String range, String date, Long mateId) {
        if (range == null || !RANGES.contains(range)) {
            throw new ApiException(CommonErrorCode.INVALID_REQUEST);
        }
        LocalDate base = date == null ? ConnectionDay.dayOf(Instant.now(clock)) : parseDate(date);
        LocalDate from;
        LocalDate to;
        switch (range) {
            case "WEEK" -> {
                from = base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                to = from.plusDays(6);
            }
            case "MONTH" -> {
                from = base.withDayOfMonth(1);
                to = base.with(TemporalAdjusters.lastDayOfMonth());
            }
            default -> {
                from = base.withDayOfYear(1);
                to = base.with(TemporalAdjusters.lastDayOfYear());
            }
        }
        boolean byMonth = "YEAR".equals(range);
        List<StudySession> list = sessions
                .endedStartedBetween(userId, ConnectionDay.startOf(from), ConnectionDay.startOf(to.plusDays(1)))
                .stream()
                .filter(s -> mateId == null || s.characterId() == mateId)
                .toList();

        // 빈 칸도 0으로: 기간의 칸을 먼저 다 만든다
        Map<String, int[]> buckets = new LinkedHashMap<>();
        if (byMonth) {
            for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(to)); m = m.plusMonths(1)) {
                buckets.put(m.toString(), new int[2]);
            }
        } else {
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                buckets.put(d.toString(), new int[2]);
            }
        }
        Map<Long, int[]> byMate = new LinkedHashMap<>(); // present · connected · count
        Set<LocalDate> days = new HashSet<>();
        int present = 0;
        int connected = 0;
        for (StudySession s : list) {
            LocalDate day = ConnectionDay.dayOf(s.getStartedAt());
            days.add(day);
            int[] b = buckets.get(byMonth ? YearMonth.from(day).toString() : day.toString());
            b[0] += s.getPresentSeconds();
            b[1] += s.getConnectedSeconds();
            int[] m = byMate.computeIfAbsent(s.characterId(), k -> new int[3]);
            m[0] += s.getPresentSeconds();
            m[1] += s.getConnectedSeconds();
            m[2]++;
            present += s.getPresentSeconds();
            connected += s.getConnectedSeconds();
        }

        Map<Long, String> names = new HashMap<>();
        Function<Long, String> nameOf = id ->
                names.computeIfAbsent(id, k -> mates.find(k).map(MateInfo::name).orElse(null));
        List<RecordsResponse.MateSummary> mateList = byMate.entrySet().stream()
                .map(e -> new RecordsResponse.MateSummary(
                        e.getKey(),
                        nameOf.apply(e.getKey()),
                        e.getValue()[0],
                        e.getValue()[1],
                        e.getValue()[2],
                        relations.stage(userId, e.getKey())))
                // 함께한 시간이 많은 순 (명세에 순서가 없어 화면 기준으로 정함)
                .sorted(Comparator.comparingInt(RecordsResponse.MateSummary::presentSeconds)
                        .thenComparingInt(RecordsResponse.MateSummary::connectedSeconds)
                        .reversed()
                        .thenComparingLong(RecordsResponse.MateSummary::mateId))
                .toList();
        List<RecordsResponse.Recent> recent = list.stream()
                .sorted(Comparator.comparing(StudySession::getEndedAt)
                        .thenComparing(StudySession::getId)
                        .reversed())
                .limit(RECENT_SIZE)
                .map(s -> new RecordsResponse.Recent(
                        s.getId(),
                        s.characterId(),
                        nameOf.apply(s.characterId()),
                        s.getStartedAt(),
                        s.getEndedAt(),
                        s.getPresentSeconds(),
                        s.getConnectedSeconds()))
                .toList();
        return new RecordsResponse(
                from,
                to,
                present,
                connected,
                days.size(),
                list.size(),
                buckets.entrySet().stream()
                        .map(e -> new RecordsResponse.Bucket(e.getKey(), e.getValue()[0], e.getValue()[1]))
                        .toList(),
                mateList,
                recent);
    }

    private static LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new ApiException(CommonErrorCode.INVALID_REQUEST);
        }
    }
}
