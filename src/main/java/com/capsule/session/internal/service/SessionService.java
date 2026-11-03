package com.capsule.session.internal.service;

import static com.capsule.session.internal.domain.SessionEvent.AWAY;
import static com.capsule.session.internal.domain.SessionEvent.BREAK;
import static com.capsule.session.internal.domain.SessionEvent.FOCUS;
import static com.capsule.session.internal.domain.SessionEvent.RETURN;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.capsule.common.error.ApiException;
import com.capsule.content.api.AvatarQuery;
import com.capsule.content.api.CharacterQuery;
import com.capsule.content.api.ContentTypes.AvatarInfo;
import com.capsule.content.api.ContentTypes.BackgroundInfo;
import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.content.api.ContentTypes.RoomInfo;
import com.capsule.content.api.RoomQuery;
import com.capsule.relation.api.RelationCommands;
import com.capsule.relation.api.RelationQuery;
import com.capsule.relation.api.RelationTypes.RelationView;
import com.capsule.relation.api.RelationTypes.SessionEventRef;
import com.capsule.relation.api.RelationTypes.StudySettlement;
import com.capsule.session.api.SessionClosingHandler;
import com.capsule.session.api.SessionTypes.SessionClosing;
import com.capsule.session.internal.domain.SessionEvent;
import com.capsule.session.internal.domain.StudySession;
import com.capsule.session.internal.dto.CharacterBrief;
import com.capsule.session.internal.dto.CharacterRef;
import com.capsule.session.internal.dto.EndRequest;
import com.capsule.session.internal.dto.EventRequest;
import com.capsule.session.internal.dto.EventResponse;
import com.capsule.session.internal.dto.Figure;
import com.capsule.session.internal.dto.HomeResponse;
import com.capsule.session.internal.dto.HomeRoom;
import com.capsule.session.internal.dto.Me;
import com.capsule.session.internal.dto.RelationBrief;
import com.capsule.session.internal.dto.ReportResponse;
import com.capsule.session.internal.dto.ResumeResponse;
import com.capsule.session.internal.dto.RoomCharacter;
import com.capsule.session.internal.dto.RoomDetail;
import com.capsule.session.internal.dto.RoomView;
import com.capsule.session.internal.dto.StartRequest;
import com.capsule.session.internal.dto.StartResponse;
import com.capsule.session.internal.repository.SessionEventRepository;
import com.capsule.session.internal.repository.StudySessionRepository;
import com.capsule.user.api.ActiveSessionLookup;
import com.capsule.user.api.UserQuery;
import com.capsule.user.api.UserTypes.UserProfile;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final RelationQuery relationQuery;
    private final RelationCommands relationCommands;
    private final SessionClosingHandler closingHandler;
    private final ActiveSessionLookup activeSessions;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration autoCloseAfter;

    SessionService(
            StudySessionRepository sessions,
            SessionEventRepository events,
            UserQuery users,
            AvatarQuery avatars,
            RoomQuery rooms,
            CharacterQuery characters,
            RelationQuery relationQuery,
            RelationCommands relationCommands,
            SessionClosingHandler closingHandler,
            ActiveSessionLookup activeSessions,
            TransactionTemplate tx,
            Clock clock,
            @Value("${capsule.session.auto-close-after}") Duration autoCloseAfter) {
        this.sessions = sessions;
        this.events = events;
        this.users = users;
        this.avatars = avatars;
        this.rooms = rooms;
        this.characters = characters;
        this.relationQuery = relationQuery;
        this.relationCommands = relationCommands;
        this.closingHandler = closingHandler;
        this.activeSessions = activeSessions;
        this.tx = tx;
        this.clock = clock;
        this.autoCloseAfter = autoCloseAfter;
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
                                .map(c -> new CharacterBrief(c.id(), c.name(), c.profileImageUrl()))
                                .toList()))
                .toList();
        return new HomeResponse(activeSessions.find(userId).orElse(null), list);
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
                            new RelationBrief(v.level(), v.levelName(), v.totalStudySeconds()));
                })
                .toList();
        // 다른 룸의 진행 중 세션이면 이 룸 버튼은 그대로 둔다 (시작하면 409 → H18 팝업)
        Long activeSessionId = sessions.findByUserIdAndEndedAtIsNull(userId)
                .filter(s -> s.roomId() == roomId)
                .map(StudySession::id)
                .orElse(null);
        return new RoomDetail(room.id(), room.name(), room.description(), room.thumbnailUrl(), list, activeSessionId);
    }

    // ---- 16 공부 시작 · 17 이어하기

    @Transactional
    public StartResponse start(long userId, StartRequest req) {
        Integer minutes = req.targetMinutes();
        if (minutes == null || minutes < 5 || minutes > 180 || minutes % 5 != 0) {
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
            throw new ApiException(CONFLICT, "SESSION_ALREADY_IN_PROGRESS", Map.of("sessionId", s.id()));
        });
        BackgroundInfo background = rooms.defaultBackground(room.id()).orElseThrow();
        // ponytail: 동시에 두 번 시작하면 부분 유니크 인덱스가 막지만 409가 아니라 500. 연타가 문제 되면 예외를 409로 바꿀 것
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
        SessionEvent last = list.isEmpty() ? null : list.getLast();
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
        s.heartbeat(now);
        // 서버가 넣은 두 이벤트는 호감도에 반영하지 않는다 (api.md 17)
        StudyTime time = StudyTime.of(s.startedAt(), list, now);

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
            return new EventResponse(duplicate.get().id(), false);
        }
        SessionEvent stateAt = null; // 이 이벤트 시각의 상태 (늦게 도착한 이벤트도 제자리에서 판단)
        for (SessionEvent e : list) {
            if (!e.occurredAt().isAfter(at)) {
                stateAt = e;
            }
        }
        if (stateAt != null && BREAK.equals(stateAt.type()) && (AWAY.equals(type) || RETURN.equals(type))) {
            // 명세는 "서버가 무시"뿐이라 저장하지 않고 eventId null로 돌려준다
            return new EventResponse(null, false);
        }

        SessionEvent saved = events.save(new SessionEvent(s.id(), type, at));
        boolean firstFocus = FOCUS.equals(type)
                && list.stream()
                        .noneMatch(
                                e -> FOCUS.equals(e.type()) && !e.occurredAt().isAfter(at));
        relationCommands.applySessionEvent(
                new SessionEventRef(userId, s.characterId(), s.id(), saved.id(), type, firstFocus));
        return new EventResponse(saved.id(), true);
    }

    @Transactional
    public void heartbeat(long userId, long sessionId) {
        inProgress(userId, sessionId).heartbeat(now());
    }

    // ---- 22 공부 종료 · 23 리포트 · 자동 마감

    @Transactional
    public ReportResponse end(long userId, long sessionId, EndRequest req) {
        StudySession s = owned(sessions.findWithLockById(sessionId), userId);
        if (!s.inProgress()) {
            return toReport(s, relationQuery.settlementOf(s.userId(), s.characterId(), s.id()));
        }
        Instant now = now();
        Integer study = req.studySeconds();
        Integer present = req.presentSeconds();
        if (study == null
                || present == null
                || present < 0
                || present > study
                || study > Duration.between(s.startedAt(), now).toSeconds()) {
            throw new ApiException(BAD_REQUEST, "INVALID_STUDY_TIME");
        }
        List<SessionEvent> list = events.findBySessionIdOrderByOccurredAtAscIdAsc(s.id());
        StudyTime time = StudyTime.of(s.startedAt(), list, now);
        // TODO(judgment): J-11 프론트 값이 서버 계산값(time)과 "크게" 다를 때의 기준 미정. 지금은 부등식 검증만 하고 프론트 값을 그대로 씀
        return close(s, now, "NORMAL", present, study - present, time.breakSeconds(), list);
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
    @Scheduled(fixedDelayString = "PT10M")
    public void autoCloseStale() {
        Instant cutoff = now().minus(autoCloseAfter);
        for (long id : sessions.findStaleIds(cutoff)) {
            try {
                tx.executeWithoutResult(status -> autoClose(id, cutoff));
            } catch (RuntimeException e) {
                log.warn("세션 {} 자동 마감 실패, 다음 배치에서 다시 시도", id, e); // 하나가 실패해도 나머지는 닫히게
            }
        }
    }

    private void autoClose(long sessionId, Instant cutoff) {
        StudySession s = sessions.findWithLockById(sessionId).orElseThrow();
        Instant end = s.lastHeartbeatAt() != null ? s.lastHeartbeatAt() : s.startedAt();
        if (!s.inProgress() || !end.isBefore(cutoff)) {
            return; // 그 사이 종료됐거나 이어하기함
        }
        List<SessionEvent> list = events.findBySessionIdOrderByOccurredAtAscIdAsc(s.id());
        StudyTime time = StudyTime.of(s.startedAt(), list, end);
        close(s, end, "AUTO_CLOSED", time.presentSeconds(), time.awaySeconds(), time.breakSeconds(), list);
    }

    private ReportResponse close(
            StudySession s,
            Instant endedAt,
            String endType,
            int present,
            int away,
            int breakSeconds,
            List<SessionEvent> list) {
        s.close(endedAt, endType, present, away, breakSeconds, (int)
                list.stream().filter(e -> RETURN.equals(e.type())).count());
        long characterId = s.characterId();
        StudySettlement settlement = relationCommands.settleStudyTime(s.userId(), characterId, s.id(), present + away);
        s.recordClosingLine(closingHandler.close(new SessionClosing(
                s.id(), s.userId(), characterId, character(characterId).name(), endType, present + away)));
        return toReport(s, settlement);
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
