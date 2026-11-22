package com.capsule.dialogue.repository;

import com.capsule.dialogue.domain.ChatMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdAndSessionIdOrderById(long conversationId, long sessionId);

    /** 아직 카드로 못 쓴 메시지 전부 (이번 연결 + 앞선 종료에서 카드 쓰기에 실패한 것) */
    List<ChatMessage> findByConversationIdAndRecappedAtIsNullOrderById(long conversationId);

    /** 카드에 넣은 메시지 표시 (2026-11-23: 지우지 않고 대화방 기록으로 남김) */
    @Modifying
    @Query("update ChatMessage m set m.recappedAt = :at where m.id in :ids")
    void markRecapped(List<Long> ids, Instant at);

    /** 32 대화방 기록: 최신순, beforeId보다 오래된 것 (커서) */
    @Query(
            "select m from ChatMessage m where m.conversationId = :conversationId and m.id < :beforeId order by m.id desc")
    List<ChatMessage> findPage(long conversationId, long beforeId, Pageable page);

    boolean existsByConversationId(long conversationId);

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
