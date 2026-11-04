package com.capsule.common.config;

/**
 * Swagger 화면의 묶음 이름 = 명세(api.md) '분류'. 컨트롤러 메서드에 @Tag(name = ApiTags.AUTH)처럼 쓴다.
 * 화면에 보이는 순서는 OpenApiConfig에 적은 순서.
 * 메서드 · 클래스의 @Tag에는 name만 쓴다. description을 넣으면 springdoc이 전역 태그 목록을 다시 만들어 순서가 깨진다.
 * 태그 설명이 필요하면 OpenApiConfig의 new Tag().description(...)에 적는다
 */
public final class ApiTags {

    public static final String AUTH = "인증";
    public static final String ONBOARDING_HOME = "온보딩 · 홈";
    public static final String STUDY_SESSION = "공부 세션";
    public static final String DIALOGUE = "대화";
    public static final String REPORT = "리포트";

    private ApiTags() {}
}
