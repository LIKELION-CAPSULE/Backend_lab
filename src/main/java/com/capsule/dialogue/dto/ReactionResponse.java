package com.capsule.dialogue.dto;

import com.capsule.credit.api.CreditTypes.Credits;
import java.util.List;
import java.util.UUID;

/**
 * 이벤트 반응(api.md 19). 호감도는 상태 변화 전송(18) 응답에 있다
 *
 * @param charged 이번에 깎인 크레딧 (음성 20, 아니면 0, 2026-11-25 19 '음성')
 * @param credits 차감 뒤 크레딧 (33 Credits). tts: true로 보냈을 때만, 아니면 null
 */
public record ReactionResponse(UUID requestId, List<ReactionCharacter> characters, int charged, Credits credits) {

    public ReactionResponse(UUID requestId, List<ReactionCharacter> characters) {
        this(requestId, characters, 0, null);
    }
}
