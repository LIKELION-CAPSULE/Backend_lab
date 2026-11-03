package com.capsule.content.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface AvatarRepository extends JpaRepository<Avatar, Long> {

    List<Avatar> findByIsActiveTrueOrderBySortOrderAscIdAsc();
}
