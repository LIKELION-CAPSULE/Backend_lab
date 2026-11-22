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
     * 2026-11-23 스터디룸 삭제 뒤: roomId는 늘 0(룸이 없음, 생성자 모양을 지키려고 남김), personality는 해시태그 값.
     * model은 준비 중 메이트면 null
     *
     * @param profileImageUrl 캐릭터 사진 (연결 화면)
     * @param homeImageUrl 옛 홈 포스터 카드 전신 그림, 없으면 null
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
     * 친밀도 해금 콘텐츠 하나 (api.md 22 relation.unlocked).
     *
     * @param type EXPRESSION · VOICE_LINE
     * @param expressionName EXPRESSION이면 모델의 표정 이름
     * @param assetUrl 보이스 대사 음성 (CDN 주소를 붙인 값), 없으면 null
     */
    public record UnlockInfo(
            long id, String type, String title, int unlockStage, String expressionName, String assetUrl) {}

    public record MotionRef(String group, String file) {}

    public record ExpressionRef(String name, String file) {}

    /** 반응 매핑에서 고른 모션·표정. 둘 다 없을 수 있음 */
    public record Reaction(MotionRef motion, ExpressionRef expression) {
        public static final Reaction NONE = new Reaction(null, null);
    }
}
