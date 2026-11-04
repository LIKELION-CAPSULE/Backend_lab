package com.capsule.dialogue.repository;

import com.capsule.dialogue.domain.EvalRun;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalRunRepository extends JpaRepository<EvalRun, Long> {
    List<EvalRun> findAllByOrderByIdDesc();

    List<EvalRun> findByStatus(String status);
}
