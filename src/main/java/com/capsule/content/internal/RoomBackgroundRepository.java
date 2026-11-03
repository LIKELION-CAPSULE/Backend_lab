package com.capsule.content.internal;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoomBackgroundRepository extends JpaRepository<RoomBackground, Long> {

    Optional<RoomBackground> findByRoomIdAndIsDefaultTrue(long roomId);
}
