package com.capsule.dialogue.controller;

import com.capsule.common.config.ApiTags;
import com.capsule.common.response.ApiResponse;
import com.capsule.common.security.LoginUser;
import com.capsule.dialogue.dto.ChatRoomResponse;
import com.capsule.dialogue.service.ChatRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = ApiTags.DIALOGUE)
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoom;

    @Operation(summary = "32. 대화방 기록", description = "이 메이트와 나눈 대화를 이전 연결까지 최신순, 커서 페이지 (명세: api.md 32)")
    @GetMapping("/api/mates/{mateId}/messages")
    public ApiResponse<ChatRoomResponse> messages(
            @LoginUser long userId,
            @PathVariable long mateId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(chatRoom.page(userId, mateId, cursor, size));
    }
}
