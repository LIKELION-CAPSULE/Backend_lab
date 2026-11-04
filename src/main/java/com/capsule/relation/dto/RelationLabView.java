package com.capsule.relation.dto;

import com.capsule.relation.api.RelationTypes.RelationView;
import java.time.Instant;
import java.util.List;

/** 흐름 테스트 상태판: 지금 관계 + 최근 호감도 변화 기록 (최근 것부터) */
public record RelationLabView(RelationView relation, List<LogLine> logs) {

    public record LogLine(String reason, String signal, int delta, Long sessionId, Instant createdAt) {}
}
