package com.capsule.session.controller;

import com.capsule.common.config.ApiTags;
import com.capsule.common.security.LoginUser;
import com.capsule.session.dto.EndRequest;
import com.capsule.session.dto.EventRequest;
import com.capsule.session.dto.EventResponse;
import com.capsule.session.dto.HeartbeatRequest;
import com.capsule.session.dto.HomeResponse;
import com.capsule.session.dto.ReportResponse;
import com.capsule.session.dto.ResumeResponse;
import com.capsule.session.dto.RoomDetail;
import com.capsule.session.dto.StartRequest;
import com.capsule.session.dto.StartResponse;
import com.capsule.session.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SessionController {

    private final SessionService service;

    SessionController(SessionService service) {
        this.service = service;
    }

    @Tag(name = ApiTags.ONBOARDING_HOME)
    @Operation(summary = "11. 홈 조회", description = "오늘 공부 시간 · 이어하기 · 들어갈 수 있는 룸 1개 (명세: api.md 11)")
    @GetMapping("/api/home")
    HomeResponse home(@LoginUser long userId) {
        return service.home(userId);
    }

    @Tag(name = ApiTags.ONBOARDING_HOME)
    @Operation(summary = "12. 스터디룸 상세 조회", description = "룸 정보 + 캐릭터 1명(성격 태그 배열) + 나와의 관계(함께 n분 · Lv) (명세: api.md 12)")
    @GetMapping("/api/rooms/{roomId}")
    RoomDetail room(@LoginUser long userId, @PathVariable long roomId) {
        return service.room(userId, roomId);
    }

    @Tag(name = ApiTags.STUDY_SESSION)
    @Operation(summary = "16. 공부 시작", description = "룸 · 캐릭터 1명 · 목표 시간으로 세션 생성 + 캐릭터 · 내 프로필 이미지 (명세: api.md 16)")
    @PostMapping("/api/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    StartResponse start(@LoginUser long userId, @Valid @RequestBody StartRequest req) {
        try {
            return service.start(userId, req);
        } catch (DataIntegrityViolationException e) {
            // 연타로 두 요청이 동시에 들어오면 늦은 쪽이 유니크 인덱스에 막힌다. 트랜잭션이 끝난 뒤라 여기서 409로 바꿈
            throw SessionService.alreadyInProgress(
                    service.inProgressSessionId(userId).orElseThrow(() -> e));
        }
    }

    @Tag(name = ApiTags.STUDY_SESSION)
    @Operation(summary = "17. 세션 이어하기", description = "비정상 종료된 세션 이어하기. 끊긴 구간은 자동 휴식 처리 (명세: api.md 17)")
    @PostMapping("/api/sessions/{sessionId}/resume")
    ResumeResponse resume(@LoginUser long userId, @PathVariable long sessionId) {
        return service.resume(userId, sessionId);
    }

    @Tag(name = ApiTags.STUDY_SESSION)
    @Operation(
            summary = "18. 공부 상태 기록",
            description = "상태가 바뀌거나 동작(졸음 · 하품 · 손 흔들기 · 고개 돌림)이 감지될 때마다 1건씩 전송"
                    + " → 저장 + 호감도 반영 → eventId + 캐릭터별 '알아채는' 동작 · 호감도 (명세: api.md 18)")
    @PostMapping("/api/sessions/{sessionId}/events")
    ResponseEntity<EventResponse> event(
            @LoginUser long userId, @PathVariable long sessionId, @RequestBody EventRequest req) {
        EventResponse res = service.recordEvent(userId, sessionId, req);
        return ResponseEntity.status(res.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(res);
    }

    @Tag(name = ApiTags.STUDY_SESSION)
    @Operation(summary = "21. heartbeat", description = "1분마다 생존 신호. 비정상 종료 판단 기준 (명세: api.md 21)")
    @PostMapping("/api/sessions/{sessionId}/heartbeat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void heartbeat(
            @LoginUser long userId, @PathVariable long sessionId, @RequestBody(required = false) HeartbeatRequest req) {
        // 본문이 없거나 값이 틀려도 생존 신호는 서비스가 갱신한 뒤라, 커밋 후에 400을 돌려준다
        if (!service.heartbeat(userId, sessionId, req)) {
            throw SessionService.invalidStudyTime();
        }
    }

    @Tag(name = ApiTags.REPORT)
    @Operation(
            summary = "22. 공부 종료",
            description = "공부 종료 + 정산 + 관계 갱신 → 공부 시간 · 자리 지킴률 · 달성률 · 호감도 변화 (명세: api.md 22)")
    @PostMapping("/api/sessions/{sessionId}/end")
    ReportResponse end(@LoginUser long userId, @PathVariable long sessionId, @RequestBody EndRequest req) {
        return service.end(userId, sessionId, req);
    }

    @Tag(name = ApiTags.REPORT)
    @Operation(summary = "23. 리포트 조회", description = "리포트 · 카드 다시 불러오기 (공부 종료와 같은 응답) (명세: api.md 23)")
    @GetMapping("/api/sessions/{sessionId}/report")
    ReportResponse report(@LoginUser long userId, @PathVariable long sessionId) {
        return service.report(userId, sessionId);
    }
}
