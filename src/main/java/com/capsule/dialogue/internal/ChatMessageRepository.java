package com.capsule.dialogue.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdAndSessionIdOrderById(long conversationId, long sessionId);

    Optional<ChatMessage> findByConversationIdAndRequestIdAndSender(long conversationId, UUID requestId, String sender);
}
