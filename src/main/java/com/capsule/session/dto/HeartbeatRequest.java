package com.capsule.session.dto;

/** 그 시점까지의 공부 시간(쉬는 시간 제외)과 그중 자리 지킴. 검사는 공부 종료(22)와 같아서 서비스가 한다 */
public record HeartbeatRequest(Integer studySeconds, Integer presentSeconds) {}
