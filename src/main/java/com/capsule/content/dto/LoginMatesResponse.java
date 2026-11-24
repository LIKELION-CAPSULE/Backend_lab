package com.capsule.content.dto;

import com.capsule.content.api.ContentTypes.ModelInfo;
import java.util.List;

/**
 * api.md 48 로그인 화면 메이트 (2026-11-25). model은 31 메이트 상세와 같은 값 (프론트가 로그인 뒤 재사용).
 * cardImageUrl = 모델을 불러오기 전 · 실패했을 때의 정지 이미지 (11 홈 카드와 같은 그림)
 */
public record LoginMatesResponse(List<Mate> mates) {

    public record Mate(long id, String name, String ageRating, String cardImageUrl, ModelInfo model) {}
}
