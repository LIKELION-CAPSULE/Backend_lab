package com.capsule.user.internal.repository;

import com.capsule.user.internal.domain.UserMemory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {

    List<UserMemory> findByUserId(long userId);
}
