package com.capsule.session.dto;

/** api.md 21. 연결 시작부터 누적(초). 검사는 서비스가 한다 */
public record HeartbeatRequest(
        Integer connectedSeconds, Integer presentSeconds, Integer cameraOnSeconds, Integer pipSeconds) {}
