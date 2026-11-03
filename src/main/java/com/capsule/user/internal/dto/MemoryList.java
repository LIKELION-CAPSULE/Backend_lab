package com.capsule.user.internal.dto;

import java.time.LocalDate;
import java.util.List;

public record MemoryList(List<MemoryResponse> memories, List<LocalDate> datesWithMemories) {}
