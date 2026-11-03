package com.capsule.user.internal.dto;

/** cookie = Set-Cookie 헤더 값 */
public record Issued(String accessToken, String cookie) {}
