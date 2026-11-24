package com.capsule.content.dto;

import java.util.List;

/** 관리용 API 요청 · 응답 (ADR 0033) */
public final class ContentAdminRequests {

    private ContentAdminRequests() {}

    /**
     * 모델 하나와 그 표정 · 모션 · 반응표. 경로는 Supabase 버킷 안 경로(ADR 0024), 반응은 이 요청의 표정 · 모션 이름을 가리킨다
     *
     * @param format CUBISM · ALIVING2D_V2, 비우면 ALIVING2D_V2
     * @param storagePrefix 예: male/v1/
     * @param modelJsonPath 예: model.json (prefix 기준)
     * @param enginePath · playerPath · reactionsPath Aliving2D 런타임 (버킷 루트 기준, 예: aliving2d/v4/engine.js). CUBISM이면 비움
     */
    public record RegisterModel(
            String format,
            String storagePrefix,
            String modelJsonPath,
            Double scale,
            Integer offsetX,
            Integer offsetY,
            List<Expression> expressions,
            List<Motion> motions,
            List<Reaction> reactions,
            String enginePath,
            String playerPath,
            String reactionsPath) {}

    /** 이미 등록된 모델의 Aliving2D 런타임 경로 (버킷 루트 기준). null인 칸은 지운다 (2026-11-25) */
    public record SetRuntime(String enginePath, String playerPath, String reactionsPath) {}

    /**
     * 아바타 (api.md 9, 2026-11-25). 모델은 먼저 /internal/content/models로 등록
     *
     * @param thumbnailPath 선택 카드 이미지 (버킷 안 경로, 예: avatars/hiyori/v1/thumb.png)
     * @param sortOrder 비우면 맨 뒤
     */
    public record CreateAvatar(String name, Long modelId, String thumbnailPath, Integer sortOrder) {}

    /** 아바타 켜고 끄기 · 순서. 고른 사용자가 있으니 지우지 않고 끈다 (null 칸은 그대로) */
    public record UpdateAvatar(Boolean active, Integer sortOrder) {}

    public record AvatarSaved(long avatarId, long modelId, boolean active, int sortOrder) {}

    /** name = 반응 응답 · 감정 이름으로 쓰이는 이름, label = 한국어 표시 이름 */
    public record Expression(String name, String label, String file) {}

    public record Motion(String group, String name, String label, String file) {}

    /**
     * @param intensity 1~3 또는 null(강도 무관)
     * @param expression 이 요청의 표정 name (없으면 null)
     * @param motion 이 요청의 모션 name (없으면 null)
     */
    public record Reaction(String trigger, Integer intensity, String expression, String motion) {}

    public record ModelRegistered(long modelId, String storagePrefix, int expressions, int motions, int reactions) {}

    /** 모델이 없으면 준비 중 메이트(comingSoon) */
    public record CreateMate(String name, String oneLiner, String intro, List<String> hashtags, Long modelId) {}

    public record MateSaved(long mateId, Long modelId, boolean comingSoon) {}

    public record SetModel(Long modelId) {}
}
