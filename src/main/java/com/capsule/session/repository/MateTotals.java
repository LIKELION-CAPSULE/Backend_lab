package com.capsule.session.repository;

import java.time.Instant;

/** 메이트별 끝난 연결 합계 (StudySessionRepository.mateTotals, api.md 37) */
public interface MateTotals {

    Long getMateId();

    Instant getFirstMetAt();

    Instant getLastConnectedAt();

    Long getConnectionCount();

    Long getPresentSeconds();

    Long getConnectedSeconds();
}
