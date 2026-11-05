package com.capsule.dialogue.repository;

import com.capsule.dialogue.domain.CharacterMemory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CharacterMemoryRepository extends JpaRepository<CharacterMemory, Long> {

    List<CharacterMemory> findByUserIdAndCharacterIdOrderById(long userId, long characterId);

    /** 회원 탈퇴 */
    @Modifying
    @Query("delete from CharacterMemory m where m.userId = :userId")
    void deleteByUserId(long userId);
}
