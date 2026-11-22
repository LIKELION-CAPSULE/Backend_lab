package com.capsule.session.controller;

import com.capsule.common.config.ApiTags;
import com.capsule.common.response.ApiResponse;
import com.capsule.common.security.LoginUser;
import com.capsule.session.dto.MyMatesResponse;
import com.capsule.session.dto.RecordsResponse;
import com.capsule.session.service.RecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 내 메이트 · 내 기록 (api.md 37 · 38, 분류 '연결'). /api/mates/me는 31의 /api/mates/{mateId}보다 먼저 맞는다 */
@RestController
@RequiredArgsConstructor
public class RecordController {

    private final RecordService service;

    @Tag(name = ApiTags.STUDY_SESSION)
    @Operation(summary = "37. 내 메이트", description = "한 번이라도 연결한 메이트와 메이트별 친밀도 · 표정 컬렉션 · 최근 기록 (명세: api.md 37)")
    @GetMapping("/api/mates/me")
    public ApiResponse<MyMatesResponse> myMates(@LoginUser long userId) {
        return ApiResponse.ok(service.myMates(userId));
    }

    @Tag(name = ApiTags.STUDY_SESSION)
    @Operation(
            summary = "38. 내 기록",
            description = "기간별(WEEK · MONTH · YEAR) 함께한 시간 · 연결 시간, 달력, 메이트별 요약, 최근 연결 (명세: api.md 38)")
    @GetMapping("/api/records")
    public ApiResponse<RecordsResponse> records(
            @LoginUser long userId,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Long mateId) {
        return ApiResponse.ok(service.records(userId, range, date, mateId));
    }
}
