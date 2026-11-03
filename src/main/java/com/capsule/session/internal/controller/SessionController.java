package com.capsule.session.internal.controller;

import com.capsule.common.security.LoginUser;
import com.capsule.session.internal.dto.EndRequest;
import com.capsule.session.internal.dto.EventRequest;
import com.capsule.session.internal.dto.EventResponse;
import com.capsule.session.internal.dto.HomeResponse;
import com.capsule.session.internal.dto.ReportResponse;
import com.capsule.session.internal.dto.ResumeResponse;
import com.capsule.session.internal.dto.RoomDetail;
import com.capsule.session.internal.dto.StartRequest;
import com.capsule.session.internal.dto.StartResponse;
import com.capsule.session.internal.service.SessionService;
import jakarta.validation.Valid;
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

    @GetMapping("/api/home")
    HomeResponse home(@LoginUser long userId) {
        return service.home(userId);
    }

    @GetMapping("/api/rooms/{roomId}")
    RoomDetail room(@LoginUser long userId, @PathVariable long roomId) {
        return service.room(userId, roomId);
    }

    @PostMapping("/api/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    StartResponse start(@LoginUser long userId, @Valid @RequestBody StartRequest req) {
        return service.start(userId, req);
    }

    @PostMapping("/api/sessions/{sessionId}/resume")
    ResumeResponse resume(@LoginUser long userId, @PathVariable long sessionId) {
        return service.resume(userId, sessionId);
    }

    @PostMapping("/api/sessions/{sessionId}/events")
    ResponseEntity<EventResponse> event(
            @LoginUser long userId, @PathVariable long sessionId, @RequestBody EventRequest req) {
        EventResponse res = service.recordEvent(userId, sessionId, req);
        return ResponseEntity.status(res.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(res);
    }

    @PostMapping("/api/sessions/{sessionId}/heartbeat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void heartbeat(@LoginUser long userId, @PathVariable long sessionId) {
        service.heartbeat(userId, sessionId);
    }

    @PostMapping("/api/sessions/{sessionId}/end")
    ReportResponse end(@LoginUser long userId, @PathVariable long sessionId, @RequestBody EndRequest req) {
        return service.end(userId, sessionId, req);
    }

    @GetMapping("/api/sessions/{sessionId}/report")
    ReportResponse report(@LoginUser long userId, @PathVariable long sessionId) {
        return service.report(userId, sessionId);
    }
}
