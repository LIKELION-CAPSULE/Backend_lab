package com.capsule.content.internal.repository;

import com.capsule.content.internal.domain.Avatar;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvatarRepository extends JpaRepository<Avatar, Long> {

    List<Avatar> findByIsActiveTrueOrderBySortOrderAscIdAsc();
}
