package com.capsule.user.service;

import com.capsule.common.error.ApiException;
import com.capsule.common.error.CommonErrorCode;
import com.capsule.common.time.ClockConfig;
import com.capsule.user.api.ConnectionOwnerLookup;
import com.capsule.user.domain.BetaFeedback;
import com.capsule.user.domain.UserErrorCode;
import com.capsule.user.dto.FeedbackCreated;
import com.capsule.user.dto.FeedbackRequest;
import com.capsule.user.repository.BetaFeedbackRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 베타 피드백 (api.md 42 · 23 betaFeedbackAvailable). 스크린샷은 파일 저장소가 정해지면 (확정 필요) */
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final Set<String> KINDS = Set.of(BetaFeedback.RATING, "BUG", "SUGGESTION");
    private static final int TEXT_MAX = 1000;
    /** 하루는 오전 5시(KST)에 바뀐다 */
    private static final Duration DAY_SHIFT = Duration.ofHours(5);

    private final BetaFeedbackRepository feedback;
    private final ConnectionOwnerLookup connections;
    private final Clock clock;

    @Transactional
    public FeedbackCreated submit(long userId, FeedbackRequest req) {
        String kind = req.kind();
        if (kind == null || !KINDS.contains(kind)) throw invalid();
        boolean rating = BetaFeedback.RATING.equals(kind);
        Integer score = req.score();
        if (rating ? score == null || score < 1 || score > 4 : score != null) throw invalid();
        String text =
                req.text() == null || req.text().isBlank() ? null : req.text().strip();
        if (!rating && text == null) throw invalid();
        if (text != null && text.length() > TEXT_MAX) throw invalid();
        Long connectionId = req.connectionId();
        if (connectionId != null && !connections.owns(userId, connectionId)) throw invalid();

        Instant now = Instant.now(clock);
        LocalDate day = dayOf(now);
        if (rating && feedback.existsByUserIdAndKindAndFeedbackDay(userId, kind, day)) {
            throw new ApiException(UserErrorCode.FEEDBACK_ALREADY_TODAY);
        }
        try {
            BetaFeedback saved =
                    feedback.saveAndFlush(new BetaFeedback(userId, kind, score, text, connectionId, day, now));
            return new FeedbackCreated(saved.getId());
        } catch (DataIntegrityViolationException e) { // 같은 날 평가를 동시에 두 번 보냄
            throw new ApiException(UserErrorCode.FEEDBACK_ALREADY_TODAY);
        }
    }

    @Transactional(readOnly = true)
    public boolean ratingAvailableToday(long userId) {
        return !feedback.existsByUserIdAndKindAndFeedbackDay(userId, BetaFeedback.RATING, dayOf(Instant.now(clock)));
    }

    static LocalDate dayOf(Instant at) {
        return at.minus(DAY_SHIFT).atZone(ClockConfig.KST).toLocalDate();
    }

    private static ApiException invalid() {
        return new ApiException(CommonErrorCode.INVALID_REQUEST);
    }
}
