package com.capsule.dialogue.controller;

import com.capsule.common.config.ApiTags;
import com.capsule.common.response.ApiResponse;
import com.capsule.common.security.LoginUser;
import com.capsule.dialogue.dto.MessageRatingRequest;
import com.capsule.dialogue.dto.MessageReportRequest;
import com.capsule.dialogue.dto.MessageReportResponse;
import com.capsule.dialogue.service.MessageFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages/{requestId}")
@Tag(name = ApiTags.DIALOGUE)
@RequiredArgsConstructor
public class MessageFeedbackController {

    private final MessageFeedbackService feedback;

    @Operation(summary = "40. 답변 평가", description = "메이트 답장에 UP · DOWN. 다시 누르면 덮어쓰고 null이면 취소 (명세: api.md 40)")
    @PutMapping("/rating")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rate(@LoginUser long userId, @PathVariable UUID requestId, @RequestBody MessageRatingRequest request) {
        feedback.rate(userId, requestId, request.rating());
    }

    @Operation(summary = "41. 답변 신고", description = "메이트 답장 신고. 신고한 답장은 대화방(32)과 다음 답장의 이전 대화에서 빠진다 (명세: api.md 41)")
    @PostMapping("/report")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MessageReportResponse> report(
            @LoginUser long userId, @PathVariable UUID requestId, @Valid @RequestBody MessageReportRequest request) {
        return ApiResponse.ok(new MessageReportResponse(feedback.report(userId, requestId, request)));
    }
}
