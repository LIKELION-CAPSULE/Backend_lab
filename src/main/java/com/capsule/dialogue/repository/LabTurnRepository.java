package com.capsule.dialogue.repository;

import com.capsule.dialogue.domain.LabTurn;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface LabTurnRepository extends JpaRepository<LabTurn, Long> {
    List<LabTurn> findByLabSessionIdOrderById(long labSessionId);

    long countByLabSessionId(long labSessionId);

    @Transactional
    @Modifying
    @Query("delete from LabTurn t where t.labSessionId = :labSessionId")
    void deleteBySession(long labSessionId);
}
