package com.capsule.dialogue.repository;

import com.capsule.dialogue.domain.DialogueStateEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DialogueStateRepository extends JpaRepository<DialogueStateEntity, Long> {
    Optional<DialogueStateEntity> findBySessionIdAndCharacterId(long sessionId, long characterId);

    @Modifying
    @Query("delete from DialogueStateEntity d where d.sessionId = :sessionId")
    void deleteBySession(long sessionId);
}
