package com.capsule.content.repository;

import com.capsule.content.domain.Avatar;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvatarRepository extends JpaRepository<Avatar, Long> {

    List<Avatar> findByIsActiveTrueOrderBySortOrderAscIdAsc();
}
