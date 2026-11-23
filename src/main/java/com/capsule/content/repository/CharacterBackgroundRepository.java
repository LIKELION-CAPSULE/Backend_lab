package com.capsule.content.repository;

import com.capsule.content.domain.CharacterBackground;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CharacterBackgroundRepository extends JpaRepository<CharacterBackground, Long> {

    /** 이 메이트의 활성 배경, 정렬 순서 */
    List<CharacterBackground> findByCharacterIdAndIsActiveTrueOrderBySortOrderAscIdAsc(long characterId);
}
