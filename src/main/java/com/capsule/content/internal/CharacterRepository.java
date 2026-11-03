package com.capsule.content.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface CharacterRepository extends JpaRepository<CharacterEntity, Long> {

    List<CharacterEntity> findByRoomIdAndIsActiveTrueOrderBySortOrderAscIdAsc(long roomId);
}
