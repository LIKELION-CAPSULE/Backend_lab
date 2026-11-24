package com.capsule.dialogue.repository;

import com.capsule.dialogue.domain.ConversationRecap;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ConversationRecapRepository extends JpaRepository<ConversationRecap, Long> {

    /** 최근 것부터 */
    List<ConversationRecap> findByConversationIdOrderByIdDesc(long conversationId);

    /** 47 대화 초기화 */
    @Modifying
    @Query("delete from ConversationRecap r where r.conversationId = :conversationId")
    int deleteByConversationId(long conversationId);
}
