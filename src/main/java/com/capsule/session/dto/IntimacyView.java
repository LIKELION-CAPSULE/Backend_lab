package com.capsule.session.dto;

import java.util.List;

/** api.md 22 · 23 relation: 친밀도 (2026-11-23 5단계) */
public record IntimacyView(
        int stageBefore,
        int stageAfter,
        String stageName,
        boolean stageUp,
        int creditedSeconds,
        Integer nextStageSeconds,
        int stageProgress,
        List<Unlocked> unlocked) {

    /** 이번에 열린 해금 콘텐츠 */
    public record Unlocked(String type, String title, int unlockStage, String expressionName, String assetUrl) {}

    /** api.md 16 relation: 지금 단계 */
    public record Now(int stage, String stageName) {}
}
