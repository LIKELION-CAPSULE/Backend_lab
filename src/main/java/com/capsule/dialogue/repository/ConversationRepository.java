package com.capsule.dialogue.repository;

import com.capsule.dialogue.domain.Conversation;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByUserIdAndCharacterId(long userId, long characterId);

    @Transactional // 메시지 전송은 LLM을 트랜잭션 밖에서 부르려고 트랜잭션 없이 시작한다
    @Modifying
    @Query(value = """
            INSERT INTO conversations (user_id, character_id, created_at)
            VALUES (:userId, :characterId, :now)
            ON CONFLICT (user_id, character_id) DO NOTHING""", nativeQuery = true)
    void insertIfAbsent(long userId, long characterId, Instant now);

    /** 회원 탈퇴. chat_messages · conversation_recaps는 DB의 ON DELETE CASCADE로 같이 지워진다 */
    @Modifying
    @Query("delete from Conversation c where c.userId = :userId")
    void deleteByUserId(long userId);
}
