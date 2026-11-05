package com.capsule.user.repository;

import com.capsule.user.domain.UserMemory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {

    List<UserMemory> findByUserId(long userId);

    @Modifying
    @Query("delete from UserMemory m where m.userId = :userId")
    void deleteByUserId(long userId);
}
