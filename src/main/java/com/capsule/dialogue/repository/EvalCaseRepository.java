package com.capsule.dialogue.repository;

import com.capsule.dialogue.domain.EvalCase;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalCaseRepository extends JpaRepository<EvalCase, Long> {
    List<EvalCase> findAllByOrderByIdDesc();
}
