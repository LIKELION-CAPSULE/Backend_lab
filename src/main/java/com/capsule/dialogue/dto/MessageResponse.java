package com.capsule.dialogue.dto;

import com.capsule.content.api.ContentTypes.ExpressionRef;
import com.capsule.content.api.ContentTypes.MotionRef;
import com.capsule.credit.api.CreditTypes.Credits;
import com.capsule.relation.api.RelationTypes.AffinityChange;
import java.util.UUID;

/**
 * 메시지 답장 (명세 20). 요청에서 고른 캐릭터 1명분. content ~ audioBase64는 19번 characters[] 한 칸과 같은 모양.
 * emotion은 LLM이 정하고, 고정 대사(fallback)·재전송이면 null
 *
 * @param mode 이 답장에 쓴 대화 모드 (2026-11-23)
 * @param charged 이번에 깎인 크레딧 (기본 0 · 고급 30). fallback · 안전 경고 턴은 0
 * @param credits 차감 뒤 크레딧 (33 Credits). 기본 모드는 크레딧을 보지 않으므로 null
 * @param safety 안전 경고 턴이면 WARNING, 아니면 null
 * @param background 이 답장으로 배경이 바뀌었으면 새 배경, 그대로 · 재전송이면 null (2026-11-23, ADR 0032)
 * @param voice 이번 턴에 음성을 보냈는지 (28 '음성', ADR 0034). 20 · 재전송은 false
 */
public record MessageResponse(
        UUID requestId,
        long characterId,
        String content,
        String emotion,
        MotionRef motion,
        ExpressionRef expression,
        AffinityChange affinity,
        boolean fallback,
        String audioBase64,
        ChatMode mode,
        int charged,
        Credits credits,
        String safety,
        Background background,
        boolean voice) {

    public static final String SAFETY_WARNING = "WARNING";

    /** 바뀐 배경 (명세 20 '배경'). imageUrl은 CDN 주소를 붙인 값 */
    public record Background(String code, String name, String imageUrl) {}
}
