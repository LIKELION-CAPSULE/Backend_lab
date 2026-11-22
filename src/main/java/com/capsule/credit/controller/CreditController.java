package com.capsule.credit.controller;

import com.capsule.common.config.ApiTags;
import com.capsule.common.response.ApiResponse;
import com.capsule.common.security.LoginUser;
import com.capsule.credit.dto.CreditSummaryResponse;
import com.capsule.credit.dto.CreditTransactionsResponse;
import com.capsule.credit.service.CreditHistoryService;
import com.capsule.credit.service.CreditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = ApiTags.CREDIT)
@RequiredArgsConstructor
public class CreditController {

    private final CreditService credits;
    private final CreditHistoryService history;

    @Operation(summary = "33. 내 크레딧", description = "오늘 남은 · 출석분 · 초대분 · 오늘 쓴 양 · 초대 보상 횟수 (명세: api.md 33)")
    @GetMapping("/api/credits")
    public ApiResponse<CreditSummaryResponse> summary(@LoginUser long userId) {
        CreditService.Summary s = credits.summaryWithUsage(userId);
        return ApiResponse.ok(CreditSummaryResponse.of(s.credits(), s.todayUsed(), s.invitedFriends()));
    }

    @Operation(summary = "34. 크레딧 내역", description = "최신순, filter ALL · ATTENDANCE · USE · INVITE, 커서 (명세: api.md 34)")
    @GetMapping("/api/credits/transactions")
    public ApiResponse<CreditTransactionsResponse> transactions(
            @LoginUser long userId,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(history.page(userId, filter, cursor, size));
    }
}
