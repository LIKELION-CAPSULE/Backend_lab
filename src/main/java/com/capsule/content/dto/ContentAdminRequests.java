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
            List<Reaction> reactions) {}

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

    /**
     * 메이트 목록 한 줄 (도구가 '기존 메이트'를 고를 때). 비활성 메이트도 포함
     *
     * @param modelPath 붙은 모델의 storage_prefix (없으면 null)
     * @param modelFormat CUBISM · ALIVING2D_V2 (없으면 null)
     */
    public record MateRow(
            long id,
            String name,
            String oneLiner,
            Long modelId,
            String modelPath,
            String modelFormat,
            boolean comingSoon,
            boolean active) {}
}
