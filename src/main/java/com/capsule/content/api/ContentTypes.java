package com.capsule.content.api;

import java.util.List;

/** content 모듈이 내보내는 DTO 모음. 엔티티는 밖으로 내보내지 않는다. */
public final class ContentTypes {

    private ContentTypes() {}

    /**
     * Live2D 모델. modelJsonUrl = CDN 주소 + storage_prefix + model_json_path.
     * format = CUBISM · ALIVING2D_V2, 프론트가 렌더러를 고름 (api.md 9 · 31, 2026-11-23 확장)
     */
    public record ModelInfo(String format, String modelJsonUrl, double scale, int offsetX, int offsetY) {

        public static final String CUBISM = "CUBISM";

        /** 계약 확장 전 모양 (Cubism). 다른 모듈 테스트가 쓰던 생성자를 깨지 않으려고 남긴다 */
        public ModelInfo(String modelJsonUrl, double scale, int offsetX, int offsetY) {
            this(CUBISM, modelJsonUrl, scale, offsetX, offsetY);
        }
    }

    /**
     * 메이트 상세 (api.md 31). 메이트 = 캐릭터. 이미지 · 소리 주소는 CDN 주소를 붙인 값, 없으면 null
     *
     * @param ageRating ALL · ADULT
     * @param stageNames 친밀도 단계 이름 (친밀도 단계에서 씀)
     */
    public record MateInfo(
            long id,
            String name,
            String oneLiner,
            String ageRating,
            List<String> hashtags,
            String illustrationUrl,
            String cardImageUrl,
            String voiceSampleUrl,
            String world,
            String myRole,
            String intro,
            ModelInfo model,
            List<String> stageNames,
            boolean comingSoon,
            boolean active) {}

    public record AvatarInfo(long id, String name, String thumbnailUrl, boolean active, ModelInfo model) {}

    /**
     * @param profileImageUrl 캐릭터 사진 (홈 · 룸 상세 · 공부 화면)
     * @param homeImageUrl 홈 포스터 카드에 겹치는 전신 그림, 없으면 null (api.md 11, 2026-11-06 확장)
     */
    public record CharacterInfo(
            long id,
            long roomId,
            String name,
            String profileImageUrl,
            List<String> personality,
            String intro,
            boolean active,
            ModelInfo model,
            String homeImageUrl) {

        /**
         * @deprecated 홈 그림을 받는 생성자를 쓴다 (2026-11-06 확장).
         *     제거 예정 PR: 남은 사용처(테스트)를 옮긴 뒤 content 정리 PR (성욱)
         */
        @Deprecated(forRemoval = true)
        public CharacterInfo(
                long id,
                long roomId,
                String name,
                String profileImageUrl,
                List<String> personality,
                String intro,
                boolean active,
                ModelInfo model) {
            this(id, roomId, name, profileImageUrl, personality, intro, active, model, null);
        }
    }

    /**
     * characters는 활성 캐릭터만, 정렬 순서대로.
     *
     * @param thumbnailUrl 홈 포스터 카드
     * @param bannerUrl 룸 상세 가로 배너, 없으면 null (api.md 12, 2026-11-05 확장)
     */
    public record RoomInfo(
            long id,
            String name,
            String description,
            String thumbnailUrl,
            boolean active,
            List<CharacterInfo> characters,
            String bannerUrl) {

        /**
         * @deprecated 배너를 받는 생성자를 쓴다 (2026-11-05 확장).
         *     제거 예정 PR: 남은 사용처(테스트)를 옮긴 뒤 content 정리 PR (성욱)
         */
        @Deprecated(forRemoval = true)
        public RoomInfo(
                long id,
                String name,
                String description,
                String thumbnailUrl,
                boolean active,
                List<CharacterInfo> characters) {
            this(id, name, description, thumbnailUrl, active, characters, null);
        }
    }

    public record BackgroundInfo(long id, long roomId, String name, String imageUrl) {}

    public record MotionRef(String group, String file) {}

    public record ExpressionRef(String name, String file) {}

    /** 반응 매핑에서 고른 모션·표정. 둘 다 없을 수 있음 */
    public record Reaction(MotionRef motion, ExpressionRef expression) {
        public static final Reaction NONE = new Reaction(null, null);
    }
}
