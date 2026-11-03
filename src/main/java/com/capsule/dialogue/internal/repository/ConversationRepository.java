package com.capsule.dialogue.internal.repository;

import com.capsule.dialogue.internal.domain.Conversation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByUserIdAndCharacterId(long userId, long characterId);
}
