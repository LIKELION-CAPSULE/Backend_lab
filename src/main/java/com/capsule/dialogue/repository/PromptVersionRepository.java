package com.capsule.dialogue.repository;

import com.capsule.dialogue.domain.PromptVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PromptVersionRepository extends JpaRepository<PromptVersion, Long> {

    /** 이 캐릭터가 쓸 ACTIVE 후보: 캐릭터 전용이 앞, 공통(character_id NULL)이 뒤 */
    @Query("""
            select p from PromptVersion p
            where p.promptKey = :key and p.status = 'ACTIVE' and (p.characterId = :characterId or p.characterId is null)
            order by p.characterId nulls last""")
    List<PromptVersion> findActiveFor(String key, long characterId);

    /** (키, 캐릭터) 정확히 그 칸의 ACTIVE. characterId null = 공통 */
    @Query("""
            select p from PromptVersion p
            where p.promptKey = :key and p.status = 'ACTIVE'
              and (p.characterId = :characterId or (:characterId is null and p.characterId is null))""")
    Optional<PromptVersion> findActiveExact(String key, Long characterId);

    @Query("""
            select coalesce(max(p.version), 0) from PromptVersion p
            where p.promptKey = :key and (p.characterId = :characterId or (:characterId is null and p.characterId is null))""")
    int maxVersion(String key, Long characterId);

    /** 버전 목록: 키·캐릭터를 비우면 전부 */
    @Query("""
            select p from PromptVersion p
            where (:key is null or p.promptKey = :key)
              and (:characterId is null or p.characterId = :characterId or p.characterId is null)
            order by p.promptKey, p.characterId nulls first, p.version desc""")
    List<PromptVersion> search(String key, Long characterId);

    /** 공통 버전 중 이 본문과 똑같은 것이 있는지 (시드가 바뀌었는지 판단) */
    boolean existsByPromptKeyAndCharacterIdIsNullAndBody(String promptKey, String body);
}
