package com.capsule.session.dto;

/**
 * 스터디룸 캐릭터의 관계 (명세 12). level = 함께 공부한 시간 레벨, points = 호감도 0~100, stage · stageName = 호감도 구간.
 * levelName은 옛 필드(stageName과 같은 값), 지울 예정
 */
public record RelationBrief(
        int level, @Deprecated String levelName, int totalStudySeconds, int points, int stage, String stageName) {}
