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

    /** 아직 카드로 못 쓴 메시지 전부 (카드에 넣은 메시지는 지우므로, 남아 있으면 앞선 종료가 실패한 것) */
    List<ChatMessage> findByConversationIdOrderById(long conversationId);

    /** 사용자 말의 종류를 남긴다 (답장 저장 트랜잭션 안에서) */
    @Modifying(flushAutomatically = true)
    @Query("update ChatMessage m set m.signal = :signal where m.conversationId = :conversationId"
            + " and m.requestId = :requestId and m.sender = 'USER'")
    void markSignal(long conversationId, UUID requestId, String signal);

    Optional<ChatMessage> findByConversationIdAndRequestIdAndSender(long conversationId, UUID requestId, String sender);

    /** 답장을 못 남긴 사용자 말 (답하는 사이 공부가 끝남) */
    @Transactional
    @Modifying
    @Query("delete from ChatMessage m where m.conversationId = :conversationId and m.requestId = :requestId"
            + " and m.sender = 'USER'")
    void deleteQuestion(long conversationId, UUID requestId);
}
