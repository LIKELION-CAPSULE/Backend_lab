package com.capsule.dialogue.repository;

import com.capsule.dialogue.domain.LabSession;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabSessionRepository extends JpaRepository<LabSession, Long> {
    List<LabSession> findAllByOrderByIdDesc();
}
