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

    /**
     * 메이트 켜고 끄기 · 순서 (2026-11-26, 중복 메이트 정리 등). 대화 · 친밀도 기록이 있으니 지우지 않고 끈다. null 칸은 그대로
     *
     * @param active false면 홈(11) · 로그인 화면(48) 목록에서 빠지고 메이트 상세(31)는 404
     */
    public record UpdateMate(Boolean active, Integer sortOrder) {}

    public record MateState(long mateId, boolean active, int sortOrder) {}

    public record SetModel(Long modelId) {}

    /**
     * 메이트 이미지 · 목소리 샘플 (api.md 11 · 31 · 48, 2026-11-25). Aliving2D가 파일을 버킷에 올린 뒤 부른다. 경로는 버킷 안 경로(예:
     * chaerin/v1/full.png). null인 칸은 그대로, 빈 문자열("" · 공백만)이면 지운다
     *
     * @param illustrationPath 세로 일러스트 (31 illustrationUrl, 11 lastMate · 리포트 mate). png · jpg · webp
     * @param cardImagePath 홈 · 로그인 카드 (11 mates[].cardImageUrl, 48). png · jpg · webp
     * @param voiceSamplePath '목소리 듣기' (31 voiceSampleUrl). mp3
     */
    public record SetMateImages(String illustrationPath, String cardImagePath, String voiceSamplePath) {}

    /**
     * 메이트 목소리 (ADR 0036). Aliving2D가 들어 보고 고른 값과 그 값으로 만든 샘플. 네 칸을 통째로 바꾼다 (null인 칸은 비움 → 운영 설정
     * 기본값). 목소리가 바뀌면 옛 샘플은 맞지 않으므로 samplePath도 같이 보낸다
     *
     * @param voice 예: coral (영문 소문자)
     * @param model 예: gpt-4o-mini-tts
     * @param instructions 말투 지시, 1000자까지
     * @param samplePath 버킷 안 mp3 경로 (31 voiceSampleUrl)
     */
    public record SetMateVoice(String voice, String model, String instructions, String samplePath) {}

    public record MateVoice(long mateId, String voice, String model, String instructions, String samplePath) {}

    public record MateImages(long mateId, String illustrationPath, String cardImagePath, String voiceSamplePath) {}
}
