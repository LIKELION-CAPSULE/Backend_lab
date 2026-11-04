package com.capsule.dialogue.repository;

import com.capsule.dialogue.domain.LlmUsage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmUsageRepository extends JpaRepository<LlmUsage, Long> {
    List<LlmUsage> findByTraceIdOrderById(UUID traceId);

    List<LlmUsage> findTop100ByOrderByIdDesc();
}
