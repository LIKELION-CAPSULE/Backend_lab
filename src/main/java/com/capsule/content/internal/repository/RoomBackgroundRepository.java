package com.capsule.content.internal.repository;

import com.capsule.content.internal.domain.RoomBackground;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomBackgroundRepository extends JpaRepository<RoomBackground, Long> {

    Optional<RoomBackground> findByRoomIdAndIsDefaultTrue(long roomId);
}
