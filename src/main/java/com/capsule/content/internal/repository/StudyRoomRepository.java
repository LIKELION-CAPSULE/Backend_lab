package com.capsule.content.internal.repository;

import com.capsule.content.internal.domain.StudyRoom;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyRoomRepository extends JpaRepository<StudyRoom, Long> {

    List<StudyRoom> findByIsActiveTrueOrderBySortOrderAscIdAsc();
}
