package com.capsule.user.repository;

import com.capsule.user.domain.UserMemory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {

    List<UserMemory> findByUserId(long userId);
}
