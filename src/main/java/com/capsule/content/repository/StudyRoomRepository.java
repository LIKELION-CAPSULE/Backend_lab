package com.capsule.content.repository;

import com.capsule.content.domain.StudyRoom;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyRoomRepository extends JpaRepository<StudyRoom, Long> {

    List<StudyRoom> findByIsActiveTrueOrderBySortOrderAscIdAsc();
}
