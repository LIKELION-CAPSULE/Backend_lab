package com.capsule.content.internal.repository;

import com.capsule.content.internal.domain.CharacterEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CharacterRepository extends JpaRepository<CharacterEntity, Long> {

    List<CharacterEntity> findByRoomIdAndIsActiveTrueOrderBySortOrderAscIdAsc(long roomId);
}
