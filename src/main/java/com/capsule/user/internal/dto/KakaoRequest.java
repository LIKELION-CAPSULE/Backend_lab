package com.capsule.user.internal.dto;

import java.util.List;

public record KakaoRequest(String code, String redirectUri, List<Long> agreedTermVersionIds) {}
