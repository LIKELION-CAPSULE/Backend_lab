package com.capsule.dialogue.repository;

import com.capsule.dialogue.domain.ConversationRecap;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRecapRepository extends JpaRepository<ConversationRecap, Long> {

    /** 최근 것부터 */
    List<ConversationRecap> findByConversationIdOrderByIdDesc(long conversationId);
}
