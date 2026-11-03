package com.capsule.user.internal.dto;

import com.capsule.user.internal.domain.UserMemory.Type;
import java.time.LocalDate;

public record MemoryResponse(long memoryId, Type type, String title, LocalDate dueDate, String dueTime, Integer dDay) {}
