package com.capsule.user.dto;

/** GET /api/terms 항목 모양 그대로 */
public record TermVersion(long termVersionId, String code, String name, boolean required, String content) {}
