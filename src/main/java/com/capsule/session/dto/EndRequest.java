package com.capsule.session.dto;

/** api.md 22. 시간 칸은 heartbeat와 같다. endType: NORMAL(기본) · AUTO_AWAY · MINOR_LIMIT */
public record EndRequest(
        Integer connectedSeconds,
        Integer presentSeconds,
        Integer cameraOnSeconds,
        Integer pipSeconds,
        String endType) {}
