package com.capsule.dialogue.service;

import com.capsule.common.error.ApiException;
import com.capsule.common.error.CommonErrorCode;
import com.capsule.dialogue.domain.ChatMessage;
import com.capsule.dialogue.domain.ChatReport;
import com.capsule.dialogue.domain.DialogueErrorCode;
import com.capsule.dialogue.dto.MessageRatingRequest;
import com.capsule.dialogue.dto.MessageReportRequest;
import com.capsule.dialogue.repository.ChatMessageRepository;
import com.capsule.dialogue.repository.ChatReportRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 메이트 답장 평가(api.md 40) · 신고(41). 대상은 내 대화방의 답장(CHARACTER)만 (2026-11-23) */
@Service
@Transactional
@RequiredArgsConstructor
public class MessageFeedbackService {

    private final ChatMessageRepository messages;
    private final ChatReportRepository reports;
    private final Clock clock;

    /** 40. 다시 누르면 덮어쓰고 null이면 취소 */
    public void rate(long userId, UUID requestId, MessageRatingRequest.Rating rating) {
        myReply(userId, requestId).rate(rating == null ? null : rating.name());
    }

    /** 41. 신고하면 그 답장을 가린다 (대화방 32 · 다음 답장의 이전 대화에서 빠짐) */
    public long report(long userId, UUID requestId, MessageReportRequest request) {
        String detail = request.detail() == null || request.detail().isBlank()
                ? null
                : request.detail().strip();
        if (request.reason() == MessageReportRequest.Reason.OTHER && detail == null) {
            throw new ApiException(CommonErrorCode.INVALID_REQUEST);
        }
        ChatMessage reply = myReply(userId, requestId);
        if (reports.existsByUserIdAndMessageId(userId, reply.getId())) {
            throw new ApiException(DialogueErrorCode.ALREADY_REPORTED);
        }
        Instant now = Instant.now(clock);
        ChatReport saved;
        try {
            saved = reports.saveAndFlush(
                    new ChatReport(userId, reply.getId(), request.reason().name(), detail, now));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(DialogueErrorCode.ALREADY_REPORTED); // 같은 신고가 동시에 옴
        }
        reply.hide(now);
        return saved.getId();
    }

    private ChatMessage myReply(long userId, UUID requestId) {
        return messages.findMyReplies(userId, requestId).stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(DialogueErrorCode.MESSAGE_NOT_FOUND));
    }
}
