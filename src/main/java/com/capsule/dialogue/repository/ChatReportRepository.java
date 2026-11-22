package com.capsule.dialogue.repository;

import com.capsule.dialogue.domain.ChatReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatReportRepository extends JpaRepository<ChatReport, Long> {

    boolean existsByUserIdAndMessageId(long userId, long messageId);
}
