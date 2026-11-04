package com.capsule.dialogue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 실험실: 대화방에 보낼 사용자 말 (서비스와 같은 300자 한도) */
public record LabTurnRequest(@NotBlank @Size(max = 300) String text) {}
