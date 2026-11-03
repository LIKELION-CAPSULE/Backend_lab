package com.capsule.content.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReactionMapRepository extends JpaRepository<ReactionMap, Long> {

    List<ReactionMap> findByModelIdAndTriggerTypeAndIntensity(long modelId, String triggerType, short intensity);

    List<ReactionMap> findByModelIdAndTriggerTypeAndIntensityIsNull(long modelId, String triggerType);
}
