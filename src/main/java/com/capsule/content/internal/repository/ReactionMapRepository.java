package com.capsule.content.internal.repository;

import com.capsule.content.internal.domain.ReactionMap;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReactionMapRepository extends JpaRepository<ReactionMap, Long> {

    List<ReactionMap> findByModelIdAndTriggerTypeAndIntensity(long modelId, String triggerType, short intensity);

    List<ReactionMap> findByModelIdAndTriggerTypeAndIntensityIsNull(long modelId, String triggerType);
}
