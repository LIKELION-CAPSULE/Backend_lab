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

    /**
     * 32 대화방 기록: 최신순, beforeId보다 오래된 것 (커서). 사용자 말은 답장이 있는 것만 (답장 만드는 중 · 처리 중 서버가 죽어 남은 말은 뺌).
     * 신고로 가린 답장(hidden_at)은 뺀다 (41, 2026-11-23). 그 답장의 사용자 말은 남는다
     */
    @Query("select m from ChatMessage m where m.conversationId = :conversationId and m.id < :beforeId"
            + " and m.hiddenAt is null"
            + " and (m.sender = 'CHARACTER' or exists (select 1 from ChatMessage r where r.conversationId = m.conversationId"
            + " and r.requestId = m.requestId and r.sender = 'CHARACTER'))"
            + " order by m.id desc")
    List<ChatMessage> findPage(long conversationId, long beforeId, Pageable page);

    /**
     * 40 · 41: 이 사용자 대화방의 답장 (없거나 남의 것이면 빈 목록). UNIQUE가 대화방 단위라 다른 메이트 대화방에
     * 같은 requestId가 있을 수 있어 목록으로 받는다 (부르는 쪽이 가장 최근 것)
     */
    @Query("select m from ChatMessage m where m.requestId = :requestId and m.sender = 'CHARACTER'"
            + " and m.conversationId in (select c.id from Conversation c where c.userId = :userId) order by m.id desc")
    List<ChatMessage> findMyReplies(long userId, UUID requestId);

    /** 31 hasConversation: 답장까지 받은 대화가 하나라도 있는지 */
    boolean existsByConversationIdAndSender(long conversationId, String sender);

    /** 31 hasConversation: 가리지 않은 답장이 있는지 (47 대화 초기화 뒤에는 false, 2026-11-24) */
    boolean existsByConversationIdAndSenderAndHiddenAtIsNull(long conversationId, String sender);

    /**
     * 47 대화 초기화: 이 대화방 메시지를 전부 가린다(지우지 않음, 41 신고와 같은 hidden_at). 카드에 아직 안 넣은 것도
     * 넣은 것으로 표시해 다음 연결 종료의 흐름 카드에 다시 들어가지 않게
     */
    @Modifying
    @Query("update ChatMessage m set m.hiddenAt = coalesce(m.hiddenAt, :at), m.recappedAt = coalesce(m.recappedAt, :at)"
            + " where m.conversationId = :conversationId")
    int hideAll(long conversationId, Instant at);

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
