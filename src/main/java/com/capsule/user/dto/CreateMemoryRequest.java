package com.capsule.user.dto;

/** 형식 오류도 INVALID_MEMORY로 내려고 문자열로 받는다 */
public record CreateMemoryRequest(String type, String title, String dueDate, String dueTime) {}
