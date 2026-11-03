package com.capsule.user.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {

    List<UserMemory> findByUserId(long userId);
}
