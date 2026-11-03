package com.capsule.content.repository;

import com.capsule.content.domain.RoomBackground;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomBackgroundRepository extends JpaRepository<RoomBackground, Long> {

    Optional<RoomBackground> findByRoomIdAndIsDefaultTrue(long roomId);
}
