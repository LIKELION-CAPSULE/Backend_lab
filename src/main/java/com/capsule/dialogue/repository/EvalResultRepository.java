package com.capsule.dialogue.repository;

import com.capsule.dialogue.domain.EvalResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalResultRepository extends JpaRepository<EvalResult, Long> {
    List<EvalResult> findByRunIdOrderByCaseIdAscRepeatNoAsc(long runId);
}
