package com.capsule.dialogue.dto;

import java.util.List;
import java.util.UUID;

/** 이벤트 반응(api.md 19). 호감도는 상태 변화 전송(18) 응답에 있다 */
public record ReactionResponse(UUID requestId, List<ReactionCharacter> characters) {}
