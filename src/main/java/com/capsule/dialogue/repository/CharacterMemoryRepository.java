package com.capsule.dialogue.repository;

import com.capsule.dialogue.domain.CharacterMemory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CharacterMemoryRepository extends JpaRepository<CharacterMemory, Long> {

    List<CharacterMemory> findByUserIdAndCharacterIdOrderById(long userId, long characterId);
}
