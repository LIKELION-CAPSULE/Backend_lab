package com.capsule.user.dto;

/** api.md 26 */
public record PasswordResetRequest(String token, String newPassword) {}
