package com.capsule.content.api;

import com.capsule.content.api.ContentTypes.BackgroundInfo;
import java.util.List;
import java.util.Optional;

/** 메이트 배경 (api.md 16 · 17 · 20, 2026-11-23, ADR 0032). session의 연결 시작 · 이어하기와 dialogue의 배경 고르기가 쓴다 */
public interface BackgroundQuery {

    /** 이 메이트의 활성 배경, 정렬 순서. 없으면 빈 목록 */
    List<BackgroundInfo> backgrounds(long mateId);

    /**
     * 지금 배경: code가 활성 목록에 있으면 그것, 없거나 null이면 기본 배경(기본 표시가 없으면 첫 배경).
     * 배경이 없는 메이트면 빈 값
     */
    default Optional<BackgroundInfo> current(long mateId, String code) {
        List<BackgroundInfo> all = backgrounds(mateId);
        if (code != null) {
            for (BackgroundInfo b : all) {
                if (b.code().equals(code)) return Optional.of(b);
            }
        }
        return all.stream()
                .filter(BackgroundInfo::isDefault)
                .findFirst()
                .or(() -> all.stream().findFirst());
    }
}
