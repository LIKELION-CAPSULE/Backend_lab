package com.capsule.user.controller;

import com.capsule.common.config.ApiTags;
import com.capsule.common.response.ApiResponse;
import com.capsule.common.security.LoginUser;
import com.capsule.user.dto.EnterReferralRequest;
import com.capsule.user.dto.ReferralEntered;
import com.capsule.user.dto.ReferralStatus;
import com.capsule.user.service.ReferralService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** api.md 35 · 36 친구 초대 */
@RestController
@RequestMapping("/api/referrals")
@Tag(name = ApiTags.CREDIT)
@RequiredArgsConstructor
public class ReferralController {

    private final ReferralService referrals;

    @Operation(summary = "35. 초대 현황", description = "내 코드 · 링크 · 보상 받은 수 · 초대한 친구 · 코드 입력 가능 여부 (명세: api.md 35)")
    @GetMapping("/me")
    public ApiResponse<ReferralStatus> status(@LoginUser long userId) {
        return ApiResponse.ok(referrals.status(userId));
    }

    @Operation(summary = "36. 초대 코드 입력", description = "가입 7일 안에 한 번. 보상은 입력 뒤 처음 끝나는 연결에서 (명세: api.md 36)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReferralEntered> enter(@LoginUser long userId, @RequestBody EnterReferralRequest req) {
        return ApiResponse.ok(referrals.enter(userId, req.code(), req.deviceHash()));
    }
}
