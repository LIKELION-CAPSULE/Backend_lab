package com.capsule.content.api;

import java.util.List;

/** content 모듈이 내보내는 DTO 모음. 엔티티는 밖으로 내보내지 않는다. */
public final class ContentTypes {

    private ContentTypes() {}

    /** Live2D 모델. modelJsonUrl = CDN 주소 + storage_prefix + model_json_path */
    public record ModelInfo(String modelJsonUrl, double scale, int offsetX, int offsetY) {}

    public record AvatarInfo(long id, String name, String thumbnailUrl, boolean active, ModelInfo model) {}

    public record CharacterInfo(
            long id,
            long roomId,
            String name,
            String profileImageUrl,
            List<String> personality,
            String intro,
            boolean active,
            ModelInfo model) {}

    /** characters는 활성 캐릭터만, 정렬 순서대로 */
    public record RoomInfo(
            long id,
            String name,
            String description,
            String thumbnailUrl,
            boolean active,
            List<CharacterInfo> characters) {}

    public record BackgroundInfo(long id, long roomId, String name, String imageUrl) {}

    public record MotionRef(String group, String file) {}

    public record ExpressionRef(String name, String file) {}

    /** 반응 매핑에서 고른 모션·표정. 둘 다 없을 수 있음 */
    public record Reaction(MotionRef motion, ExpressionRef expression) {
        public static final Reaction NONE = new Reaction(null, null);
    }
}
