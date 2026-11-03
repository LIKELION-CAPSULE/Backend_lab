package com.capsule.dialogue.internal;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByUserIdAndCharacterId(long userId, long characterId);
}
