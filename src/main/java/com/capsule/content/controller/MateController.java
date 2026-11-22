package com.capsule.content.controller;

import com.capsule.common.config.ApiTags;
import com.capsule.common.error.ApiException;
import com.capsule.common.response.ApiResponse;
import com.capsule.content.api.ContentTypes.MateInfo;
import com.capsule.content.api.MateQuery;
import com.capsule.content.domain.ContentErrorCode;
import com.capsule.content.dto.MateDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = ApiTags.ONBOARDING_HOME)
@RequiredArgsConstructor
public class MateController {

    private final MateQuery mates;

    /** api.md 31. 준비 중 메이트도 조회는 된다(comingSoon), 비활성 · 없는 메이트는 404 */
    @Operation(summary = "31. 메이트 상세 조회", description = "미리보기 모델 · 일러스트 · 세계관 · 내 역할 · 인트로 · 준비 중 여부 (명세: api.md 31)")
    @GetMapping("/api/mates/{mateId}")
    public ApiResponse<MateDetailResponse> detail(@PathVariable long mateId) {
        MateInfo mate = mates.find(mateId)
                .filter(MateInfo::active)
                .orElseThrow(() -> new ApiException(ContentErrorCode.MATE_NOT_FOUND));
        return ApiResponse.ok(MateDetailResponse.of(mate));
    }
}
