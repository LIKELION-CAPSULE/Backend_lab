package com.capsule.content.repository;

import com.capsule.content.domain.UnlockContent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UnlockContentRepository extends JpaRepository<UnlockContent, Long> {

    /** after < unlock_stage ≤ upTo 인 활성 콘텐츠, 단계 · id 순 */
    @Query("select c from UnlockContent c where c.characterId = :characterId and c.isActive = true"
            + " and c.unlockStage > :after and c.unlockStage <= :upTo order by c.unlockStage, c.id")
    List<UnlockContent> between(long characterId, int after, int upTo);
}
