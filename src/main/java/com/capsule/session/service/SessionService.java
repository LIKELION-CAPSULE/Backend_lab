package com.capsule.session.service;

import static com.capsule.session.domain.SessionEvent.AWAY;
import static com.capsule.session.domain.SessionEvent.IDLE_TALK;
import static com.capsule.session.domain.SessionEvent.PRESENT;
import static com.capsule.session.domain.SessionEvent.RETURN;

import com.capsule.common.error.ApiException;
import com.capsule.common.error.CommonErrorCode;
import com.capsule.common.time.ClockConfig;
import com.capsule.content.api.AvatarQuery;
import com.capsule.content.api.CharacterQuery;
import com.capsule.content.api.ContentTypes.AvatarInfo;
import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.content.api.ContentTypes.MateInfo;
import com.capsule.content.api.ContentTypes.Reaction;
import com.capsule.content.api.ContentTypes.UnlockInfo;
import com.capsule.content.api.MateQuery;
import com.capsule.content.api.ReactionQuery;
import com.capsule.content.api.UnlockQuery;
import com.capsule.relation.api.RelationCommands;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.api.RelationTypes.IntimacySettlement;
import com.capsule.session.api.SessionClosingHandler;
import com.capsule.session.api.SessionTypes.SessionClosing;
import com.capsule.session.config.SessionProperties;
import com.capsule.session.domain.ConnectionTimes;
import com.capsule.session.domain.SessionErrorCode;
import com.capsule.session.domain.SessionEvent;
import com.capsule.session.domain.StudySession;
import com.capsule.session.dto.EndRequest;
import com.capsule.session.dto.EventRequest;
import com.capsule.session.dto.EventResponse;
import com.capsule.session.dto.Figure;
import com.capsule.session.dto.HeartbeatRequest;
import com.capsule.session.dto.HeartbeatResponse;
import com.capsule.session.dto.HomeResponse;
import com.capsule.session.dto.IntimacyView;
import com.capsule.session.dto.Me;
import com.capsule.session.dto.ReactionView;
import com.capsule.session.dto.ReportResponse;
import com.capsule.session.dto.ResumeResponse;
import com.capsule.session.dto.StartRequest;
import com.capsule.session.dto.StartResponse;
import com.capsule.session.repository.SessionEventRepository;
import com.capsule.session.repository.StudySessionRepository;
import com.capsule.user.api.ActiveSessionLookup;
import com.capsule.user.api.UserQuery;
import com.capsule.user.api.UserTypes.UserProfile;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 연결 (api.md 11 · 16~18 · 21~23). 자바 이름(Session)은 정리 PR에서 Connection으로 바꾼다 */
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    /** 하루는 한국 시간 오전 5시에 바뀐다 (api.md 16 '하루 기준') */
    private static final LocalTime DAY_STARTS_AT = LocalTime.of(5, 0);
    /** 만 19세 미만 하루 연결 시간 (PRD 8장, 제타와 같은 기준) */
    private static final int MINOR_DAILY_SECONDS = 3 * 60 * 60;
    /** 프론트가 고를 수 있는 끝 종류. AUTO_CLOSED는 서버 배치만 (api.md 22) */
    private static final Set<String> CLIENT_END_TYPES =
            Set.of(StudySession.NORMAL, StudySession.AUTO_AWAY, StudySession.MINOR_LIMIT);

    private final StudySessionRepository sessions;
    private final SessionEventRepository events;
    private final UserQuery users;
    private final AvatarQuery avatars;
    private final MateQuery mates;
    private final CharacterQuery characters;
    private final ReactionQuery reactions;
    private final UnlockQuery unlocks;
    private final RelationQuery relationQuery;
    private final RelationCommands relationCommands;
    private final SessionClosingHandler closingHandler;
    private final ActiveSessionLookup activeSessions;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final SessionProperties props;

    // ---- 11 홈

    @Transactional(readOnly = true)
    public HomeResponse home(long userId) {
        List<HomeResponse.MateCard> list = mates.activeMates().stream()
                .map(m -> new HomeResponse.MateCard(
                        m.id(),
                        m.name(),
                        m.oneLiner(),
                        m.ageRating(),
                        m.hashtags() == null ? List.of() : m.hashtags(),
                        m.cardImageUrl(),
                        m.comingSoon()))
                .toList();
        HomeResponse.LastMate lastMate = sessions.findFirstByUserIdOrderByStartedAtDescIdDesc(userId)
                .flatMap(s -> mates.find(s.characterId()))
                .filter(MateInfo::active) // 비활성이 된 메이트로 '다시 연결하기'를 띄우지 않게
                .map(m -> new HomeResponse.LastMate(m.id(), m.name(), m.illustrationUrl()))
                .orElse(null);
        Instant[] day = today();
        return new HomeResponse(
                sessions.presentSecondsEndedBetween(userId, day[0], day[1]),
                sessions.connectedSecondsEndedBetween(userId, day[0], day[1]),
                activeSessions.find(userId).orElse(null),
                lastMate,
                list);
    }

    // ---- 16 연결 시작 · 17 이어하기

    /** 16 · 이어하기 팝업: 진행 중인 연결이 이미 있음 */
    public static ApiException alreadyInProgress(long connectionId) {
        return new ApiException(SessionErrorCode.CONNECTION_ALREADY_IN_PROGRESS, Map.of("connectionId", connectionId));
    }

    /** 동시 시작이 유니크 인덱스에 막혔을 때 먼저 만들어진 연결 */
    @Transactional(readOnly = true)
    public Optional<Long> inProgressSessionId(long userId) {
        return sessions.findByUserIdAndEndedAtIsNull(userId).map(StudySession::getId);
    }

    @Transactional
    public StartResponse start(long userId, StartRequest req) {
        MateInfo mate = mates.find(req.mateId())
                .filter(MateInfo::active)
                .orElseThrow(() -> new ApiException(SessionErrorCode.MATE_NOT_FOUND));
        // 준비 중이면 막는다. 모델 없는 메이트는 준비 중일 때만 있다(schema CHECK)지만 화면을 못 그리니 같이 막음
        if (mate.comingSoon() || mate.model() == null) {
            throw new ApiException(SessionErrorCode.MATE_COMING_SOON);
        }
        CharacterInfo character = character(mate.id());
        UserProfile user = user(userId);
        if (user.onboardingRequired()) {
            throw new ApiException(SessionErrorCode.ONBOARDING_REQUIRED);
        }
        sessions.findByUserIdAndEndedAtIsNull(userId).ifPresent(s -> {
            throw alreadyInProgress(s.getId());
        });
        Integer minorRemaining = minorRemaining(user, null);
        if (minorRemaining != null && minorRemaining <= 0) {
            throw new ApiException(SessionErrorCode.MINOR_DAILY_LIMIT);
        }
        // 동시에 두 번 시작하면 부분 유니크 인덱스(사용자당 진행 중 연결 하나)가 막는다 → 컨트롤러가 409로 바꿈
        StudySession s = sessions.save(new StudySession(userId, character.id(), now()));
        boolean firstMeet = !sessions.existsByUserIdAndCharacterIdAndIdNot(userId, character.id(), s.getId());
        relationCommands.meet(userId, character.id());
        AvatarInfo avatar = avatar(user);
        return new StartResponse(
                s.getId(),
                s.getStartedAt(),
                figure(character),
                figure(avatar),
                me(user, avatar),
                now(),
                firstMeet,
                reaction(character.id(), "GREETING", null),
                minorRemaining,
                stageNow(mate, relationQuery.stage(userId, character.id())));
    }

    // ---- 친밀도 (2026-11-23 5단계)

    private static IntimacyView.Now stageNow(MateInfo mate, int stage) {
        return new IntimacyView.Now(stage, stageName(mate, stage));
    }

    /** 단계 이름은 메이트마다 다름 (characters.stage_names). 없으면 null */
    private static String stageName(MateInfo mate, int stage) {
        List<String> names = mate == null ? List.of() : mate.stageNames();
        return names != null && stage >= 1 && stage <= names.size() ? names.get(stage - 1) : null;
    }

    private IntimacyView intimacy(long characterId, IntimacySettlement r) {
        MateInfo mate = mates.find(characterId).orElse(null);
        List<IntimacyView.Unlocked> unlocked = unlocks.between(characterId, r.stageBefore(), r.stageAfter()).stream()
                .map(u -> new IntimacyView.Unlocked(
                        u.type(), u.title(), u.unlockStage(), u.expressionName(), u.assetUrl()))
                .toList();
        return new IntimacyView(
                r.stageBefore(),
                r.stageAfter(),
                stageName(mate, r.stageAfter()),
                r.stageUp(),
                r.creditedSeconds(),
                r.nextStageSeconds(),
                unlocked);
    }

    @Transactional
    public ResumeResponse resume(long userId, long sessionId) {
        StudySession s = inProgress(userId, sessionId);
        // 쉬는 시간이 없어져 이벤트를 넣지 않는다 (2026-11-23). 생존 신호만 갱신해 자동 마감을 미룬다
        s.heartbeat(now());
        UserProfile user = user(userId);
        AvatarInfo avatar = avatar(user);
        ConnectionTimes t = s.lastHeartbeatTimes();
        return new ResumeResponse(
                s.getId(),
                figure(character(s.characterId())),
                figure(avatar),
                me(user, avatar),
                t.connected(),
                t.present(),
                t.cameraOn(),
                t.pip(),
                minorRemaining(user, s),
                now());
    }

    // ---- 18 상태 기록 · 21 heartbeat

    @Transactional
    public EventResponse recordEvent(long userId, long sessionId, EventRequest req) {
        StudySession s = inProgress(userId, sessionId);
        String type = req.type();
        if (type == null || !SessionEvent.TYPES.contains(type) || req.occurredAt() == null) {
            throw new ApiException(SessionErrorCode.INVALID_EVENT);
        }
        // 프론트 시계가 조금 늦으면 시작 직후 이벤트가 연결 시작보다 앞설 수 있어서, 거절하지 않고 시작 시각으로 맞춘다
        Instant occurredAt = req.occurredAt().isBefore(s.getStartedAt()) ? s.getStartedAt() : req.occurredAt();
        Instant at = occurredAt.truncatedTo(ChronoUnit.MICROS); // DB 정밀도와 맞춰야 재전송이 같은 값으로 걸린다
        List<SessionEvent> list = events.findBySessionIdOrderByOccurredAtAscIdAsc(s.getId());
        Optional<SessionEvent> duplicate = list.stream()
                .filter(e -> e.getType().equals(type) && e.getOccurredAt().equals(at))
                .findFirst();
        if (duplicate.isPresent()) {
            // 재전송: 처음과 같은 반응 (강도는 그 이벤트까지의 자리 비움 횟수)
            SessionEvent e = duplicate.get();
            List<SessionEvent> upTo =
                    list.stream().filter(x -> x.getId() <= e.getId()).toList();
            return new EventResponse(e.getId(), reactionOf(s, type, upTo), false);
        }
        SessionEvent saved = events.save(new SessionEvent(s.getId(), type, at));
        List<SessionEvent> upTo = new java.util.ArrayList<>(list);
        upTo.add(saved);
        return new EventResponse(saved.getId(), reactionOf(s, type, upTo), true);
    }

    /**
     * 생존 신호는 값이 잘못돼도 항상 갱신한다 (아니면 연결 중인데 끊긴 연결이 됨). 값은 맞을 때만 저장.
     * 400을 여기서 던지면 갱신까지 롤백되므로, 맞으면 응답 · 틀리면 빈 값을 돌려주고 컨트롤러가 400으로 바꾼다
     */
    @Transactional
    public Optional<HeartbeatResponse> heartbeat(long userId, long sessionId, HeartbeatRequest req) {
        StudySession s = inProgress(userId, sessionId);
        Instant now = now();
        Optional<ConnectionTimes> t = req == null
                ? Optional.empty()
                : times(s, req.connectedSeconds(), req.presentSeconds(), req.cameraOnSeconds(), req.pipSeconds(), now);
        if (t.isEmpty()) {
            s.heartbeat(now);
            return Optional.empty();
        }
        s.heartbeat(now, t.get());
        return Optional.of(new HeartbeatResponse(minorRemaining(user(userId), s), now));
    }

    public static ApiException invalidConnectionTime() {
        return new ApiException(SessionErrorCode.INVALID_CONNECTION_TIME);
    }

    // ---- 22 연결 끊기 · 23 연결 기록 · 자동 마감

    /**
     * 대화 마무리(LLM)는 DB 연결을 잡지 않게 트랜잭션 밖에서 먼저 만들고, 잠금 · 검증 · 저장은 한 트랜잭션에서 한다.
     * 그사이 다른 요청이 먼저 끊으면 초안은 버려지고 이미 끝난 연결의 기록을 돌려준다.
     */
    public ReportResponse end(long userId, long sessionId, EndRequest req) {
        String endType = req.endType() == null ? StudySession.NORMAL : req.endType();
        if (!CLIENT_END_TYPES.contains(endType)) {
            throw new ApiException(CommonErrorCode.INVALID_REQUEST);
        }
        // 짧은 트랜잭션으로 미리 읽고(잠금 없음) 바로 연결을 돌려준다. 틀린 요청에는 LLM을 부르지 않게 검증도 먼저
        SessionClosing closing = tx.execute(status -> {
            StudySession s = owned(sessions.findById(sessionId), userId);
            if (!s.inProgress()) return null;
            ConnectionTimes t = requireTimes(s, req, now());
            return closingOf(s, endType, t.connected());
        });
        SessionClosingHandler.Draft draft = closing == null ? null : closingHandler.draft(closing);
        return tx.execute(status -> endLocked(userId, sessionId, req, endType, draft));
    }

    private ReportResponse endLocked(
            long userId, long sessionId, EndRequest req, String endType, SessionClosingHandler.Draft draft) {
        StudySession s = owned(sessions.findWithLockById(sessionId), userId);
        if (!s.inProgress()) {
            return toReport(s, relationQuery.intimacyOf(s.getUserId(), s.characterId(), s.getId()));
        }
        Instant now = now();
        ConnectionTimes t = requireTimes(s, req, now);
        return close(s, now, endType, t, draft);
    }

    @Transactional(readOnly = true)
    public ReportResponse report(long userId, long sessionId) {
        StudySession s = owned(sessions.findById(sessionId), userId);
        if (s.inProgress()) {
            throw new ApiException(SessionErrorCode.CONNECTION_IN_PROGRESS);
        }
        return toReport(s, relationQuery.intimacyOf(s.getUserId(), s.characterId(), s.getId()));
    }

    /** heartbeat가 auto-close-after(6h)보다 오래 없는 연결을 마지막 heartbeat 시각 · 값으로 닫는다 (api.md 21) */
    // ponytail: 서버 1대 전제 (ADR 0011). 여러 대가 되면 ShedLock으로 한 대만 돌게
    @Scheduled(fixedDelayString = "${capsule.session.auto-close-interval:PT10M}")
    public void autoCloseStale() {
        Instant cutoff = now().minus(props.autoCloseAfter());
        for (long id : sessions.findStaleIds(cutoff)) {
            try {
                // 연결 끊기와 같이 대화 마무리(LLM)는 트랜잭션 밖에서 먼저
                SessionClosing closing = tx.execute(status -> {
                    StudySession s = sessions.findById(id).orElseThrow();
                    return stale(s, cutoff)
                            ? closingOf(
                                    s,
                                    StudySession.AUTO_CLOSED,
                                    s.lastHeartbeatTimes().connected())
                            : null;
                });
                SessionClosingHandler.Draft draft = closing == null ? null : closingHandler.draft(closing);
                tx.executeWithoutResult(status -> {
                    StudySession s = sessions.findWithLockById(id).orElseThrow();
                    if (stale(s, cutoff)) {
                        close(s, lastAlive(s), StudySession.AUTO_CLOSED, s.lastHeartbeatTimes(), draft);
                    }
                });
            } catch (RuntimeException e) {
                log.warn("연결 {} 자동 마감 실패, 다음 배치에서 다시 시도", id, e); // 하나가 실패해도 나머지는 닫히게
            }
        }
    }

    /** 그 사이 끊겼거나 이어하기했으면 false */
    private static boolean stale(StudySession s, Instant cutoff) {
        return s.inProgress() && lastAlive(s).isBefore(cutoff);
    }

    private static Instant lastAlive(StudySession s) {
        return s.getLastHeartbeatAt() != null ? s.getLastHeartbeatAt() : s.getStartedAt();
    }

    private ReportResponse close(
            StudySession s, Instant endedAt, String endType, ConnectionTimes t, SessionClosingHandler.Draft draft) {
        List<SessionEvent> list = events.findBySessionIdOrderByOccurredAtAscIdAsc(s.getId());
        // 자리 비움은 이벤트로 세고, 카메라를 켰는데 얼굴이 안 보인 시간을 넘지 않게
        int away = Math.min(StudyTime.awaySeconds(list, endedAt), t.cameraOn() - t.present());
        int returns =
                (int) list.stream().filter(e -> RETURN.equals(e.getType())).count();
        s.close(endedAt, endType, t, Math.max(0, away), returns);
        long characterId = s.characterId();
        IntimacySettlement settlement = relationCommands.settleIntimacy(
                s.getUserId(), characterId, s.getId(), endedAt, t.present(), t.connected());
        if (settlement.stageUp()) {
            List<Long> opened = unlocks.between(characterId, settlement.stageBefore(), settlement.stageAfter()).stream()
                    .map(UnlockInfo::id)
                    .toList();
            relationCommands.recordUnlocks(s.getUserId(), opened);
        }
        s.recordClosingLine(closingHandler.close(closingOf(s, endType, t.connected()), draft));
        return toReport(s, settlement);
    }

    /** 초안 · 저장 둘 다 이걸로 만든다. studySeconds 자리에 연결 시간 (목표 시간이 없어 공부 포기 판정은 꺼짐) */
    private SessionClosing closingOf(StudySession s, String endType, int connectedSeconds) {
        return new SessionClosing(
                s.getId(),
                s.getUserId(),
                s.characterId(),
                character(s.characterId()).name(),
                endType,
                connectedSeconds,
                null);
    }

    private ReportResponse toReport(StudySession s, IntimacySettlement settlement) {
        long characterId = s.characterId();
        CharacterInfo c = character(characterId);
        Optional<MateInfo> mate = mates.find(characterId);
        return new ReportResponse(
                s.getId(),
                s.getStartedAt(),
                s.getEndedAt(),
                s.getEndType(),
                s.getConnectedSeconds(),
                s.getPresentSeconds(),
                s.getAwaySeconds(),
                s.getMessageCount(),
                s.getReturnCount(),
                new ReportResponse.MateRef(
                        characterId,
                        c.name(),
                        mate.map(MateInfo::illustrationUrl).orElse(null),
                        mate.map(MateInfo::myRole).orElse(null)),
                intimacy(characterId, settlement),
                s.getClosingLine());
    }

    // ---- 도우미

    /** 22: 21과 같은 검사 */
    private static ConnectionTimes requireTimes(StudySession s, EndRequest req, Instant now) {
        return times(s, req.connectedSeconds(), req.presentSeconds(), req.cameraOnSeconds(), req.pipSeconds(), now)
                .orElseThrow(SessionService::invalidConnectionTime);
    }

    /**
     * 21 · 22: 0 ≤ present ≤ cameraOn ≤ connected ≤ 시작부터 지난 시간, pip ≤ connected.
     * 누적값이라 마지막 heartbeat의 연결 시간보다 줄어들 수 없다 (줄여 보내 청소년 3시간을 늘리지 못하게)
     */
    private static Optional<ConnectionTimes> times(
            StudySession s, Integer connected, Integer present, Integer cameraOn, Integer pip, Instant now) {
        long elapsed = Duration.between(s.getStartedAt(), now).toSeconds();
        boolean notBackwards =
                connected == null || connected >= s.lastHeartbeatTimes().connected();
        return notBackwards && ConnectionTimes.valid(connected, present, cameraOn, pip, elapsed)
                ? Optional.of(new ConnectionTimes(connected, present, cameraOn, pip))
                : Optional.empty();
    }

    /** 18 반응: PRESENT는 없음, AWAY는 몇 번째 자리 비움인지가 강도, IDLE_TALK는 IDLE */
    private ReactionView reactionOf(StudySession s, String type, List<SessionEvent> upTo) {
        return switch (type) {
            case AWAY -> reaction(s.characterId(), AWAY, StudyTime.awayIntensity(upTo));
            case RETURN -> reaction(s.characterId(), RETURN, null);
            case IDLE_TALK -> reaction(s.characterId(), "IDLE", null);
            case PRESENT -> null;
            default -> null;
        };
    }

    private ReactionView reaction(long characterId, String trigger, Integer intensity) {
        Reaction r = reactions.pick(characterId, trigger, intensity);
        return ReactionView.of(
                trigger,
                intensity,
                r.expression() == null ? null : r.expression().name());
    }

    /**
     * 만 19세 미만이면 오늘 남은 연결 시간, 아니면 null (api.md 16 '청소년 3시간').
     * = 3시간 − (오늘 끝난 연결의 연결 시간 + 진행 중 연결의 마지막 heartbeat 연결 시간)
     */
    private Integer minorRemaining(UserProfile user, StudySession current) {
        if (!user.minor()) {
            return null;
        }
        Instant[] day = today();
        int used = sessions.connectedSecondsEndedBetween(user.id(), day[0], day[1]);
        if (current != null && current.inProgress()) {
            used += current.lastHeartbeatTimes().connected();
        }
        return Math.max(0, MINOR_DAILY_SECONDS - used);
    }

    /** 오늘(오전 5시 기준)의 [시작, 끝) */
    private Instant[] today() {
        var local = now().atZone(ClockConfig.KST);
        LocalDate day = local.toLocalTime().isBefore(DAY_STARTS_AT)
                ? local.toLocalDate().minusDays(1)
                : local.toLocalDate();
        Instant from = day.atTime(DAY_STARTS_AT).atZone(ClockConfig.KST).toInstant();
        return new Instant[] {from, from.plus(Duration.ofDays(1))};
    }

    /** 남의 연결도 없는 연결과 똑같이 404 */
    private static StudySession owned(Optional<StudySession> found, long userId) {
        return found.filter(s -> s.getUserId() == userId)
                .orElseThrow(() -> new ApiException(SessionErrorCode.CONNECTION_NOT_FOUND));
    }

    private StudySession inProgress(long userId, long sessionId) {
        StudySession s = owned(sessions.findWithLockById(sessionId), userId);
        if (!s.inProgress()) {
            throw new ApiException(SessionErrorCode.CONNECTION_ENDED);
        }
        return s;
    }

    private UserProfile user(long userId) {
        return users.find(userId).orElseThrow(() -> new ApiException(CommonErrorCode.UNAUTHORIZED));
    }

    private AvatarInfo avatar(UserProfile user) {
        return avatars.find(user.avatarId()).orElseThrow();
    }

    private CharacterInfo character(long characterId) {
        return characters.find(characterId).orElseThrow();
    }

    private static Figure figure(CharacterInfo c) {
        return new Figure(
                c.id(),
                c.name(),
                c.profileImageUrl(),
                c.model().format(),
                c.model().modelJsonUrl(),
                c.model().scale(),
                c.model().offsetX(),
                c.model().offsetY());
    }

    private static Figure figure(AvatarInfo a) {
        return new Figure(
                a.id(),
                a.name(),
                a.thumbnailUrl(),
                a.model().format(),
                a.model().modelJsonUrl(),
                a.model().scale(),
                a.model().offsetX(),
                a.model().offsetY());
    }

    private static Me me(UserProfile user, AvatarInfo avatar) {
        return new Me(user.nickname(), avatar.thumbnailUrl());
    }

    /** DB(timestamptz) 정밀도에 맞춰 응답 값과 저장 값이 같게 */
    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
