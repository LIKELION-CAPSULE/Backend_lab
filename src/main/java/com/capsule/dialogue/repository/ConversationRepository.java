package com.capsule.dialogue.repository;

import com.capsule.dialogue.domain.Conversation;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByUserIdAndCharacterId(long userId, long characterId);

    @Modifying
    @Query(value = """
            INSERT INTO conversations (user_id, character_id, created_at)
            VALUES (:userId, :characterId, :now)
            ON CONFLICT (user_id, character_id) DO NOTHING""", nativeQuery = true)
    void insertIfAbsent(long userId, long characterId, Instant now);
}
