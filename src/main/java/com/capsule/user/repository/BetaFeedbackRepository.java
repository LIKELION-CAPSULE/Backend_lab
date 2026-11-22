package com.capsule.user.repository;

import com.capsule.user.domain.BetaFeedback;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BetaFeedbackRepository extends JpaRepository<BetaFeedback, Long> {

    boolean existsByUserIdAndKindAndFeedbackDay(long userId, String kind, LocalDate feedbackDay);
}
