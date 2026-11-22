package com.capsule.user.adapter;

import com.capsule.user.api.BetaFeedbackQuery;
import com.capsule.user.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** BetaFeedbackQuery 계약 구현 (UserQueryAdapter와 같은 이유로 얇게 따로 둔다) */
@Component
@RequiredArgsConstructor
public class BetaFeedbackQueryAdapter implements BetaFeedbackQuery {

    private final FeedbackService feedback;

    @Override
    public boolean ratingAvailableToday(long userId) {
        return feedback.ratingAvailableToday(userId);
    }
}
