package com.capsule.session.service;

import static com.capsule.session.domain.SessionEvent.AWAY;
import static com.capsule.session.domain.SessionEvent.BREAK;
import static com.capsule.session.domain.SessionEvent.FOCUS;
import static com.capsule.session.domain.SessionEvent.RETURN;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.capsule.common.error.ApiException;
import com.capsule.common.time.ClockConfig;
import com.capsule.content.api.AvatarQuery;
import com.capsule.content.api.CharacterQuery;
import com.capsule.content.api.ContentTypes.AvatarInfo;
import com.capsule.content.api.ContentTypes.BackgroundInfo;
import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.content.api.ContentTypes.Reaction;
import com.capsule.content.api.ContentTypes.RoomInfo;
import com.capsule.content.api.ReactionQuery;
import com.capsule.content.api.RoomQuery;
import com.capsule.relation.api.RelationCommands;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.api.RelationTypes.AffinityChange;
import com.capsule.relation.api.RelationTypes.RelationView;
import com.capsule.relation.api.RelationTypes.SessionEventRef;
import com.capsule.relation.api.RelationTypes.StudySettlement;
import com.capsule.session.api.SessionClosingHandler;
import com.capsule.session.api.SessionTypes.SessionClosing;
import com.capsule.session.config.SessionProperties;
import com.capsule.session.domain.SessionEvent;
import com.capsule.session.domain.StudySession;
import com.capsule.session.dto.CharacterBrief;
import com.capsule.session.dto.CharacterRef;
import com.capsule.session.dto.EndRequest;
import com.capsule.session.dto.EventCharacter;
import com.capsule.session.dto.EventRequest;
import com.capsule.session.dto.EventResponse;
import com.capsule.session.dto.Figure;
import com.capsule.session.dto.HeartbeatRequest;
import com.capsule.session.dto.HomeResponse;
import com.capsule.session.dto.HomeRoom;
import com.capsule.session.dto.Me;
import com.capsule.session.dto.RelationBrief;
import com.capsule.session.dto.ReportResponse;
import com.capsule.session.dto.ResumeResponse;
import com.capsule.session.dto.RoomCharacter;
import com.capsule.session.dto.RoomDetail;
import com.capsule.session.dto.RoomView;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final StudySessionRepository sessions;
    private final SessionEventRepository events;
    private final UserQuery users;
    private final AvatarQuery avatars;
    private final RoomQuery rooms;
    private final CharacterQuery characters;
    private final ReactionQuery reactions;
    private final RelationQuery relationQuery;
    private final RelationCommands relationCommands;
    private final SessionClosingHandler closingHandler;
    private final ActiveSessionLookup activeSessions;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final SessionProperties props;

    SessionService(
            StudySessionRepository sessions,
            SessionEventRepository events,
            UserQuery users,
            AvatarQuery avatars,
            RoomQuery rooms,
            CharacterQuery characters,
            ReactionQuery reactions,
            RelationQuery relationQuery,
            RelationCommands relationCommands,
            SessionClosingHandler closingHandler,
            ActiveSessionLookup activeSessions,
            TransactionTemplate tx,
            Clock clock,
            SessionProperties props) {
        this.sessions = sessions;
        this.events = events;
        this.users = users;
        this.avatars = avatars;
        this.rooms = rooms;
        this.characters = characters;
        this.reactions = reactions;
        this.relationQuery = relationQuery;
        this.relationCommands = relationCommands;
        this.closingHandler = closingHandler;
        this.activeSessions = activeSessions;
        this.tx = tx;
        this.clock = clock;
        this.props = props;
    }

    // ---- 11 홈 · 12 룸 상세

    @Transactional(readOnly = true)
    public HomeResponse home(long userId) {
        List<HomeRoom> list = rooms.activeRooms().stream()
                .map(r -> new HomeRoom(
                        r.id(),
                        r.name(),
                        r.thumbnailUrl(),
                        r.characters().stream()
                                .map(c -> new CharacterBrief(
                                        c.id(),
                                        c.name(),
                                        c.homeImageUrl() != null ? c.homeImageUrl() : c.profileImageUrl()))
                                .toList()))
                .toList();
        LocalDate today = LocalDate.ofInstant(now(), ClockConfig.KST);
        int todayStudySeconds = sessions.studySecondsEndedBetween(
                userId,
                today.atStartOfDay(ClockConfig.KST).toInstant(),
                today.plusDays(1).atStartOfDay(ClockConfig.KST).toInstant());
        return new HomeResponse(todayStudySeconds, activeSessions.find(userId).orElse(null), list);
    }

    @Transactional(readOnly = true)
    public RoomDetail room(long userId, long roomId) {
        RoomInfo room = activeRoom(roomId);
        List<RoomCharacter> list = room.characters().stream()
                .map(c -> {
                    RelationView v = relationQuery.view(userId, c.id());
                    return new RoomCharacter(
                            c.id(),
                            c.name(),
                            c.profileImageUrl(),
                            c.personality(),
                            new RelationBrief(
                                    v.level(),
                                    v.stageName(),
                                    v.totalStudySeconds(),
                                    v.points(),
                                    v.stage(),
                                    v.stageName()));
                })
                .toList();
        // 다른 룸의 진행 중 세션이면 이 룸 버튼은 그대로 둔다 (시작하면 409 → H18 팝업)
        Long activeSessionId = sessions.findByUserIdAndEndedAtIsNull(userId)
                .filter(s -> s.roomId() == roomId)
                .map(StudySession::id)
                .orElse(null);
        return new RoomDetail(
                room.id(),
                room.name(),
                room.description(),
                room.thumbnailUrl(),
                room.bannerUrl(),
                list,
                activeSessionId);
    }

    // ---- 16 공부 시작 · 17 이어하기

    /** 16 · H18 팝업: 진행 중인 세션이 이미 있음 */
    public static ApiException alreadyInProgress(long sessionId) {
        return new ApiException(CONFLICT, "SESSION_ALREADY_IN_PROGRESS", Map.of("sessionId", sessionId));
    }

    /** 동시 시작이 유니크 인덱스에 막혔을 때 먼저 만들어진 세션 */
    @Transactional(readOnly = true)
    public Optional<Long> inProgressSessionId(long userId) {
        return sessions.findByUserIdAndEndedAtIsNull(userId).map(StudySession::id);
    }

    @Transactional
    public StartResponse start(long userId, StartRequest req) {
        Integer minutes = req.targetMinutes();
        // 3분, 또는 5~180분 5분 단위 (api.md 16, 2026-11-05 최소 3분)
        boolean valid = minutes != null
                && (props.targetMinutesExtra().contains(minutes)
                        || (minutes >= props.targetMinutesMin()
                                && minutes <= props.targetMinutesMax()
                                && minutes % props.targetMinutesStep() == 0));
        if (!valid) {
            throw new ApiException(BAD_REQUEST, "INVALID_TARGET_MINUTES");
        }
        RoomInfo room = activeRoom(req.roomId());
        CharacterInfo character = room.characters().stream()
                .filter(c -> c.id() == req.characterId())
                .findFirst()
                .orElseThrow(() -> new ApiException(BAD_REQUEST, "CHARACTER_NOT_IN_ROOM"));
        UserProfile user = user(userId);
        if (user.onboardingRequired()) {
            throw new ApiException(FORBIDDEN, "ONBOARDING_REQUIRED");
        }
        sessions.findByUserIdAndEndedAtIsNull(userId).ifPresent(s -> {
            throw alreadyInProgress(s.id());
        });
        BackgroundInfo background = rooms.defaultBackground(room.id()).orElseThrow();
        // 동시에 두 번 시작하면 부분 유니크 인덱스(사용자당 진행 중 세션 하나)가 막는다 → 컨트롤러가 409로 바꿈
        StudySession s =
                sessions.save(new StudySession(userId, room.id(), background.id(), character.id(), minutes, now()));
        relationCommands.meet(userId, character.id());
        AvatarInfo avatar = avatar(user);
        return new StartResponse(
                s.id(),
                s.startedAt(),
                minutes,
                roomView(room, background),
                figure(character),
                figure(avatar),
                me(user, avatar),
                now());
    }

    @Transactional
    public ResumeResponse resume(long userId, long sessionId) {
        StudySession s = inProgress(userId, sessionId);
        Instant now = now();
        List<SessionEvent> list = new ArrayList<>(events.findBySessionIdOrderByOccurredAtAscIdAsc(s.id()));
        // 동작 이벤트는 상태를 바꾸지 않으므로 마지막 '상태' 이벤트로 판단 (api.md 18)
        SessionEvent last =
                list.stream().filter(SessionEvent::isState).reduce((a, b) -> b).orElse(null);
        if (last == null || !BREAK.equals(last.type())) {
            // 끊긴 구간 = 휴식. heartbeat 뒤에 온 이벤트가 있으면 그 뒤부터 (그 상태가 끊긴 동안 이어지지 않게)
            Instant breakAt = s.lastHeartbeatAt() != null ? s.lastHeartbeatAt() : s.startedAt();
            if (last != null && last.occurredAt().isAfter(breakAt)) {
                breakAt = last.occurredAt();
            }
            if (breakAt.isAfter(now)) {
                breakAt = now;
            }
            list.add(events.save(new SessionEvent(s.id(), BREAK, breakAt)));
        }
        list.add(events.save(new SessionEvent(s.id(), FOCUS, now)));
        // 서버가 넣은 두 이벤트는 호감도에 반영하지 않는다 (api.md 17)
        StudyTime time = StudyTime.of(s.startedAt(), list, now);
        // 끊기기 전 heartbeat 값이 남아 있으면, 다음 heartbeat 전에 또 끊길 때 자동 마감이 옛 값으로 기록한다 → 이어하기 응답과 같은 값으로 맞춤
        s.heartbeat(now, time.studySeconds(), time.presentSeconds());

        UserProfile user = user(userId);
        AvatarInfo avatar = avatar(user);
        return new ResumeResponse(
                s.id(),
                s.targetMinutes(),
                roomView(
                        rooms.find(s.roomId()).orElseThrow(),
                        rooms.background(s.backgroundId()).orElseThrow()),
                figure(character(s.characterId())),
                figure(avatar),
                me(user, avatar),
                time.studySeconds(),
                time.breakSeconds(),
                now);
    }

    // ---- 18 상태 변화 · 21 heartbeat

    @Transactional
    public EventResponse recordEvent(long userId, long sessionId, EventRequest req) {
        StudySession s = inProgress(userId, sessionId);
        String type = req.type();
        if (type == null || !SessionEvent.TYPES.contains(type) || req.occurredAt() == null) {
            throw new ApiException(BAD_REQUEST, "INVALID_EVENT");
        }
        // 프론트 시계가 서버보다 조금 늦으면 시작 직후 FOCUS가 세션 시작보다 앞설 수 있어서, 거절하지 않고 시작 시각으로 맞춘다
        Instant occurredAt = req.occurredAt().isBefore(s.startedAt()) ? s.startedAt() : req.occurredAt();
        Instant at = occurredAt.truncatedTo(ChronoUnit.MICROS); // DB 정밀도와 맞춰야 재전송이 같은 값으로 걸린다
        List<SessionEvent> list = events.findBySessionIdOrderByOccurredAtAscIdAsc(s.id());

        Optional<SessionEvent> duplicate = list.stream()
                .filter(e -> e.type().equals(type) && e.occurredAt().equals(at))
                .findFirst();
        if (duplicate.isPresent()) {
            // 재전송: 호감도는 다시 반영하지 않고 처음 반영된 변화를 돌려준다
            SessionEvent e = duplicate.get();
            SessionEventRef ref = eventRef(s, e.id(), type, firstFocus(list, e.id(), type, at));
            return new EventResponse(e.id(), eventCharacters(ref, relationQuery.changeOf(ref)), false);
        }
        SessionEvent stateAt = null; // 이 이벤트 시각의 상태 (늦게 도착한 이벤트도 제자리에서 판단). 동작 이벤트는 상태가 아님
        for (SessionEvent e : list) {
            if (e.isState() && !e.occurredAt().isAfter(at)) {
                stateAt = e;
            }
        }
        boolean motion = SessionEvent.MOTION_TYPES.contains(type);
        if (stateAt != null && BREAK.equals(stateAt.type()) && (AWAY.equals(type) || RETURN.equals(type) || motion)) {
            // 명세는 "서버가 무시"뿐이라 저장하지 않고 eventId null로 돌려준다
            return EventResponse.ignored();
        }
        // 동작 이벤트 쿨다운 (api.md 18): 같은 type이 motion-cooldown(1분) 안에 다시 오면 무시. 늦게 도착한 앞선 이벤트도 걸리게 앞뒤로 본다
        if (motion
                && list.stream()
                        .anyMatch(e -> e.type().equals(type)
                                && Duration.between(e.occurredAt(), at).abs().compareTo(props.motionCooldown()) < 0)) {
            return EventResponse.ignored();
        }

        SessionEvent saved = events.save(new SessionEvent(s.id(), type, at));
        // 자리 비움을 끝낸 상태 이벤트면 비운 시간을 같이 넘긴다 (relation이 오래 비웠으면 감점).
        // 늦게 도착한 이벤트가 이미 다른 상태 이벤트로 끝난 AWAY를 또 끝내지 않게, 그 AWAY 뒤에 상태 이벤트가 없을 때만
        SessionEvent away = stateAt;
        boolean closesAway = !motion
                && away != null
                && AWAY.equals(away.type())
                && !AWAY.equals(type)
                && list.stream().noneMatch(e -> e.isState() && e.occurredAt().isAfter(away.occurredAt()));
        Integer awaySeconds =
                closesAway ? (int) Duration.between(away.occurredAt(), at).toSeconds() : null;
        SessionEventRef ref = new SessionEventRef(
                s.userId(),
                s.characterId(),
                s.id(),
                saved.id(),
                type,
                firstFocus(list, saved.id(), type, at),
                awaySeconds);
        return new EventResponse(saved.id(), eventCharacters(ref, relationCommands.applySessionEvent(ref)), true);
    }

    /**
     * 생존 신호는 값이 잘못돼도 항상 갱신한다 (아니면 공부 중인데 끊긴 세션이 됨). 값은 맞을 때만 저장.
     * 400을 여기서 던지면 갱신까지 롤백되므로, 값이 맞았는지만 돌려주고 컨트롤러가 400으로 바꾼다
     */
    @Transactional
    public boolean heartbeat(long userId, long sessionId, HeartbeatRequest req) {
        StudySession s = inProgress(userId, sessionId);
        Instant now = now();
        Integer study = req == null ? null : req.studySeconds();
        Integer present = req == null ? null : req.presentSeconds();
        if (!validStudyTime(s, study, present, now)) {
            s.heartbeat(now);
            return false;
        }
        s.heartbeat(now, study, present);
        return true;
    }

    public static ApiException invalidStudyTime() {
        return new ApiException(BAD_REQUEST, "INVALID_STUDY_TIME");
    }

    // ---- 22 공부 종료 · 23 리포트 · 자동 마감

    /**
     * 대화 마무리(LLM, 최대 5초)는 DB 연결을 잡지 않게 트랜잭션 밖에서 먼저 만들고, 잠금 · 검증 · 저장은 한 트랜잭션에서 한다.
     * 그사이 다른 요청이 먼저 종료하면 초안은 버려지고 이미 끝난 세션의 리포트를 돌려준다.
     */
    public ReportResponse end(long userId, long sessionId, EndRequest req) {
        // 짧은 트랜잭션으로 미리 읽고(잠금 없음) 바로 연결을 돌려준다. 틀린 요청에는 LLM을 부르지 않게 검증도 먼저
        SessionClosing closing = tx.execute(status -> {
            StudySession s = owned(sessions.findById(sessionId), userId);
            if (!s.inProgress()) return null;
            validateStudyTime(s, req.studySeconds(), req.presentSeconds(), now());
            return closingOf(s, "NORMAL", req.studySeconds()); // studySeconds = present + away
        });
        SessionClosingHandler.Draft draft = closing == null ? null : closingHandler.draft(closing);
        return tx.execute(status -> endLocked(userId, sessionId, req, draft));
    }

    private ReportResponse endLocked(long userId, long sessionId, EndRequest req, SessionClosingHandler.Draft draft) {
        StudySession s = owned(sessions.findWithLockById(sessionId), userId);
        if (!s.inProgress()) {
            return toReport(s, relationQuery.settlementOf(s.userId(), s.characterId(), s.id()));
        }
        Instant now = now();
        Integer study = req.studySeconds();
        Integer present = req.presentSeconds();
        validateStudyTime(s, study, present, now);
        List<SessionEvent> list = events.findBySessionIdOrderByOccurredAtAscIdAsc(s.id());
        StudyTime time = StudyTime.of(s.startedAt(), list, now);
        // TODO(judgment): J-11 프론트 값이 서버 계산값(time)과 "크게" 다를 때의 기준 미정. 지금은 부등식 검증만 하고 프론트 값을 그대로 씀
        return close(s, now, "NORMAL", present, study - present, time.breakSeconds(), list, draft);
    }

    @Transactional(readOnly = true)
    public ReportResponse report(long userId, long sessionId) {
        StudySession s = owned(sessions.findById(sessionId), userId);
        if (s.inProgress()) {
            throw new ApiException(CONFLICT, "SESSION_IN_PROGRESS");
        }
        return toReport(s, relationQuery.settlementOf(s.userId(), s.characterId(), s.id()));
    }

    /** heartbeat가 auto-close-after(6h)보다 오래 없는 세션을 마지막 heartbeat 시각으로 닫는다 (api.md 21) */
    // ponytail: 서버 1대 전제 (ADR 0011). 여러 대가 되면 ShedLock으로 한 대만 돌게
    @Scheduled(fixedDelayString = "${capsule.session.auto-close-interval:PT10M}")
    public void autoCloseStale() {
        Instant cutoff = now().minus(props.autoCloseAfter());
        for (long id : sessions.findStaleIds(cutoff)) {
            try {
                // 공부 종료와 같이 대화 마무리(LLM)는 트랜잭션 밖에서 먼저
                SessionClosing closing = tx.execute(status -> {
                    StudySession s = sessions.findById(id).orElseThrow();
                    return autoClosePlan(s, cutoff)
                            .map(p -> closingOf(s, "AUTO_CLOSED", p.present() + p.away()))
                            .orElse(null);
                });
                SessionClosingHandler.Draft draft = closing == null ? null : closingHandler.draft(closing);
                tx.executeWithoutResult(status -> autoClose(id, cutoff, draft));
            } catch (RuntimeException e) {
                log.warn("세션 {} 자동 마감 실패, 다음 배치에서 다시 시도", id, e); // 하나가 실패해도 나머지는 닫히게
            }
        }
    }

    private record AutoClosePlan(Instant end, int present, int away, int breakSeconds, List<SessionEvent> list) {}

    /** 자동 마감할 시각과 시간. 그 사이 종료됐거나 이어하기했으면 빈 값 */
    private Optional<AutoClosePlan> autoClosePlan(StudySession s, Instant cutoff) {
        Instant end = s.lastHeartbeatAt() != null ? s.lastHeartbeatAt() : s.startedAt();
        if (!s.inProgress() || !end.isBefore(cutoff)) {
            return Optional.empty();
        }
        List<SessionEvent> list = events.findBySessionIdOrderByOccurredAtAscIdAsc(s.id());
        StudyTime time = StudyTime.of(s.startedAt(), list, end);
        // 마지막 heartbeat가 보낸 프론트 값으로 기록 (api.md 21). 값을 받은 적이 없으면 이벤트로 계산
        Integer study = s.heartbeatStudySeconds();
        Integer present = s.heartbeatPresentSeconds();
        boolean sent = study != null && present != null;
        return Optional.of(new AutoClosePlan(
                end,
                sent ? present : time.presentSeconds(),
                sent ? study - present : time.awaySeconds(),
                time.breakSeconds(),
                list));
    }

    private void autoClose(long sessionId, Instant cutoff, SessionClosingHandler.Draft draft) {
        StudySession s = sessions.findWithLockById(sessionId).orElseThrow();
        autoClosePlan(s, cutoff)
                .ifPresent(p ->
                        close(s, p.end(), "AUTO_CLOSED", p.present(), p.away(), p.breakSeconds(), p.list(), draft));
    }

    private ReportResponse close(
            StudySession s,
            Instant endedAt,
            String endType,
            int present,
            int away,
            int breakSeconds,
            List<SessionEvent> list,
            SessionClosingHandler.Draft draft) {
        s.close(endedAt, endType, present, away, breakSeconds, (int)
                list.stream().filter(e -> RETURN.equals(e.type())).count());
        long characterId = s.characterId();
        StudySettlement settlement =
                relationCommands.settleStudy(s.userId(), characterId, s.id(), present + away, present);
        s.recordClosingLine(closingHandler.close(closingOf(s, endType, present + away), draft));
        return toReport(s, settlement);
    }

    /** 초안 · 저장 둘 다 이걸로 만든다. 목표 시간을 빠뜨리면 공부 포기(GIVE_UP) 판정이 꺼진다 */
    private SessionClosing closingOf(StudySession s, String endType, int studySeconds) {
        return new SessionClosing(
                s.id(),
                s.userId(),
                s.characterId(),
                character(s.characterId()).name(),
                endType,
                studySeconds,
                s.targetMinutes());
    }

    private ReportResponse toReport(StudySession s, StudySettlement settlement) {
        long characterId = s.characterId();
        return new ReportResponse(
                s.id(),
                rooms.find(s.roomId()).map(RoomInfo::name).orElse(null),
                s.startedAt(),
                s.endedAt(),
                s.endType(),
                s.focusSeconds() + s.distractSeconds(),
                s.focusSeconds(),
                s.distractSeconds(),
                s.returnCount(),
                new CharacterRef(characterId, character(characterId).name()),
                settlement,
                s.closingLine());
    }

    // ---- 도우미

    /** 공부 종료(22)·heartbeat(21)의 프론트 공부 시간 검사: 0 ≤ present ≤ study ≤ 시작부터 지난 시간 */
    private static void validateStudyTime(StudySession s, Integer study, Integer present, Instant now) {
        if (!validStudyTime(s, study, present, now)) {
            throw invalidStudyTime();
        }
    }

    private static boolean validStudyTime(StudySession s, Integer study, Integer present, Instant now) {
        return study != null
                && present != null
                && present >= 0
                && present <= study
                && study <= Duration.between(s.startedAt(), now).toSeconds();
    }

    /** 이 이벤트 시각까지(자기 자신 빼고) FOCUS가 없으면 세션 첫 FOCUS */
    private static boolean firstFocus(List<SessionEvent> list, long eventId, String type, Instant at) {
        return FOCUS.equals(type)
                && list.stream()
                        .noneMatch(e -> e.id() != eventId
                                && FOCUS.equals(e.type())
                                && !e.occurredAt().isAfter(at));
    }

    /** 이미 반영된 이벤트를 다시 읽을 때 (changeOf는 비운 시간을 쓰지 않음) */
    private static SessionEventRef eventRef(StudySession s, long eventId, String type, boolean firstFocus) {
        return new SessionEventRef(s.userId(), s.characterId(), s.id(), eventId, type, firstFocus, null);
    }

    /** 18 응답의 캐릭터별 모션·표정·호감도. 뼈대는 세션당 캐릭터 1명 */
    private List<EventCharacter> eventCharacters(SessionEventRef ref, AffinityChange affinity) {
        // 이벤트 반응 받기(19)와 같은 trigger 규칙. 여기서는 강도를 받지 않으므로 AWAY는 1
        String type = ref.eventType();
        String trigger = FOCUS.equals(type) ? (ref.firstFocusOfSession() ? "GREETING" : "BREAK_END") : type;
        Reaction reaction = reactions.pick(ref.characterId(), trigger, AWAY.equals(trigger) ? 1 : null);
        return List.of(new EventCharacter(ref.characterId(), reaction.motion(), reaction.expression(), affinity));
    }

    /** 남의 세션도 없는 세션과 똑같이 404 */
    private static StudySession owned(Optional<StudySession> found, long userId) {
        return found.filter(s -> s.userId() == userId)
                .orElseThrow(() -> new ApiException(NOT_FOUND, "SESSION_NOT_FOUND"));
    }

    private StudySession inProgress(long userId, long sessionId) {
        StudySession s = owned(sessions.findWithLockById(sessionId), userId);
        if (!s.inProgress()) {
            throw new ApiException(CONFLICT, "SESSION_ENDED");
        }
        return s;
    }

    private RoomInfo activeRoom(long roomId) {
        return rooms.find(roomId)
                .filter(RoomInfo::active)
                .orElseThrow(() -> new ApiException(NOT_FOUND, "ROOM_NOT_FOUND"));
    }

    private UserProfile user(long userId) {
        return users.find(userId).orElseThrow(() -> new ApiException(UNAUTHORIZED, "UNAUTHORIZED"));
    }

    private AvatarInfo avatar(UserProfile user) {
        return avatars.find(user.avatarId()).orElseThrow();
    }

    private CharacterInfo character(long characterId) {
        return characters.find(characterId).orElseThrow();
    }

    private static RoomView roomView(RoomInfo room, BackgroundInfo background) {
        return new RoomView(room.id(), room.name(), background.imageUrl());
    }

    private static Figure figure(CharacterInfo c) {
        return new Figure(
                c.id(),
                c.name(),
                c.profileImageUrl(),
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
