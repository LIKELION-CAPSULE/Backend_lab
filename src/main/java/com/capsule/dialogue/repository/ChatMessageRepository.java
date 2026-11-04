package com.capsule.dialogue.repository;

import com.capsule.dialogue.domain.ChatMessage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdAndSessionIdOrderById(long conversationId, long sessionId);

    Optional<ChatMessage> findByConversationIdAndRequestIdAndSender(long conversationId, UUID requestId, String sender);

    /** 답장을 못 남긴 사용자 말 (답하는 사이 공부가 끝남) */
    @Transactional
    @Modifying
    @Query("delete from ChatMessage m where m.conversationId = :conversationId and m.requestId = :requestId"
            + " and m.sender = 'USER'")
    void deleteQuestion(long conversationId, UUID requestId);
}
