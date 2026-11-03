package com.capsule.content.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface StudyRoomRepository extends JpaRepository<StudyRoom, Long> {

    List<StudyRoom> findByIsActiveTrueOrderBySortOrderAscIdAsc();
}
