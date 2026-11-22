package com.capsule.session.controller;

import com.capsule.common.config.ApiTags;
import com.capsule.common.response.ApiResponse;
import com.capsule.common.security.LoginUser;
import com.capsule.session.dto.EndRequest;
import com.capsule.session.dto.EventRequest;
import com.capsule.session.dto.EventResponse;
import com.capsule.session.dto.HeartbeatRequest;
import com.capsule.session.dto.HomeResponse;
import com.capsule.session.dto.ReportResponse;
import com.capsule.session.dto.ResumeResponse;
import com.capsule.session.dto.StartRequest;
import com.capsule.session.dto.StartResponse;
import com.capsule.session.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 2026-11-23 /api/sessions → /api/connections, 12 스터디룸 상세 삭제 (api.md 공통 규칙) */
@RestController
@RequiredArgsConstructor
public class SessionController {

    private final SessionService service;

    @Tag(name = ApiTags.ONBOARDING_HOME)
    @Operation(summary = "11. 홈 조회", description = "오늘 공부 시간 · 이어하기 · 마지막 메이트 · 메이트 리스트 (명세: api.md 11)")
    @GetMapping("/api/home")
    public ApiResponse<HomeResponse> home(@LoginUser long userId) {
        return ApiResponse.ok(service.home(userId));
    }

    @Tag(name = ApiTags.STUDY_SESSION)
    @Operation(summary = "16. 연결 시작", description = "메이트 1명과 연결 생성 + 메이트 · 내 아바타 모델 (명세: api.md 16)")
    @PostMapping("/api/connections")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StartResponse> start(@LoginUser long userId, @Valid @RequestBody StartRequest req) {
        try {
            return ApiResponse.ok(service.start(userId, req));
        } catch (DataIntegrityViolationException e) {
            // 연타로 두 요청이 동시에 들어오면 늦은 쪽이 유니크 인덱스에 막힌다. 트랜잭션이 끝난 뒤라 여기서 409로 바꿈
            throw SessionService.alreadyInProgress(
                    service.inProgressSessionId(userId).orElseThrow(() -> e));
        }
    }

    @Tag(name = ApiTags.STUDY_SESSION)
    @Operation(summary = "17. 연결 이어하기", description = "비정상 종료된 연결 이어하기. 끊긴 구간은 자동 휴식 처리 (명세: api.md 17)")
    @PostMapping("/api/connections/{connectionId}/resume")
    public ApiResponse<ResumeResponse> resume(@LoginUser long userId, @PathVariable long connectionId) {
        return ApiResponse.ok(service.resume(userId, connectionId));
    }

    @Tag(name = ApiTags.STUDY_SESSION)
    @Operation(
            summary = "18. 상태 기록",
            description = "상태가 바뀌거나 동작(졸음 · 하품 · 손 흔들기 · 고개 돌림)이 감지될 때마다 1건씩 전송"
                    + " → 저장 + 호감도 반영 → eventId + 캐릭터별 '알아채는' 동작 · 호감도 (명세: api.md 18)")
    @PostMapping("/api/connections/{connectionId}/events")
    public ResponseEntity<ApiResponse<EventResponse>> event(
            @LoginUser long userId, @PathVariable long connectionId, @RequestBody EventRequest req) {
        EventResponse res = service.recordEvent(userId, connectionId, req);
        return ResponseEntity.status(res.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.ok(res));
    }

    @Tag(name = ApiTags.STUDY_SESSION)
    @Operation(summary = "21. heartbeat", description = "1분마다 생존 신호. 비정상 종료 판단 기준 (명세: api.md 21)")
    @PostMapping("/api/connections/{connectionId}/heartbeat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void heartbeat(
            @LoginUser long userId,
            @PathVariable long connectionId,
            @RequestBody(required = false) HeartbeatRequest req) {
        // 본문이 없거나 값이 틀려도 생존 신호는 서비스가 갱신한 뒤라, 커밋 후에 400을 돌려준다
        if (!service.heartbeat(userId, connectionId, req)) {
            throw SessionService.invalidStudyTime();
        }
    }

    @Tag(name = ApiTags.REPORT)
    @Operation(summary = "22. 연결 끊기", description = "연결 끊기 + 정산 + 관계 갱신 → 시간 · 호감도 변화 (명세: api.md 22)")
    @PostMapping("/api/connections/{connectionId}/end")
    public ApiResponse<ReportResponse> end(
            @LoginUser long userId, @PathVariable long connectionId, @RequestBody EndRequest req) {
        return ApiResponse.ok(service.end(userId, connectionId, req));
    }

    @Tag(name = ApiTags.REPORT)
    @Operation(summary = "23. 연결 기록 조회", description = "연결 기록 다시 불러오기 (연결 끊기와 같은 응답) (명세: api.md 23)")
    @GetMapping("/api/connections/{connectionId}")
    public ApiResponse<ReportResponse> report(@LoginUser long userId, @PathVariable long connectionId) {
        return ApiResponse.ok(service.report(userId, connectionId));
    }
}
