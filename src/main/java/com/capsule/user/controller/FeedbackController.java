package com.capsule.user.controller;

import com.capsule.common.config.ApiTags;
import com.capsule.common.response.ApiResponse;
import com.capsule.common.security.LoginUser;
import com.capsule.user.dto.FeedbackCreated;
import com.capsule.user.dto.FeedbackRequest;
import com.capsule.user.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** api.md 42 베타 피드백 */
@RestController
@Tag(name = ApiTags.REPORT)
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedback;

    @Operation(summary = "42. 베타 피드백", description = "연결 기록 위 평가(하루 1번) · 의견 보내기 (명세: api.md 42)")
    @PostMapping("/api/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FeedbackCreated> submit(@LoginUser long userId, @RequestBody FeedbackRequest req) {
        return ApiResponse.ok(feedback.submit(userId, req));
    }
}
