package com.capsule.relation.dto;

/**
 * 흐름 테스트의 호감도 맞추기. null인 칸은 그대로 둔다.
 *
 * @param sessionId 점수를 맞출 때 필수: 진행 중 세션의 기록으로 남겨 리포트 계산과 맞춘다
 */
public record RelationAdjustRequest(Integer points, Integer presentMinutes, Long sessionId) {}
