package com.capsule.user.dto;

import java.util.List;

public record KakaoRequest(String code, String redirectUri, List<Long> agreedTermVersionIds) {}
