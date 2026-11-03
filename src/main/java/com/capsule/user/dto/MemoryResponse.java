package com.capsule.user.dto;

import com.capsule.user.domain.UserMemory.Type;
import java.time.LocalDate;

public record MemoryResponse(long memoryId, Type type, String title, LocalDate dueDate, String dueTime, Integer dDay) {}
