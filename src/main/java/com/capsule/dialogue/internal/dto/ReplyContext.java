package com.capsule.dialogue.internal.dto;

import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.user.api.UserTypes.MemoryInfo;
import java.util.List;

/** 답장 생성 입력. history = 이번 세션의 앞선 메시지 (이번 userMessage는 빠짐) */
public record ReplyContext(
        CharacterInfo character,
        String summary,
        List<ChatLine> history,
        List<MemoryInfo> memories,
        String userMessage) {}
