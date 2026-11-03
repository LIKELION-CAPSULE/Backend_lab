package com.capsule.dialogue.client;

import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.dialogue.dto.ChatLine;
import com.capsule.dialogue.dto.LlmReaction;
import com.capsule.dialogue.dto.LlmReply;
import com.capsule.dialogue.dto.ReplyContext;
import java.util.List;
import java.util.Map;

/** 대화 LLM 자리. 뼈대는 {@link FakeLlmClient}만 있고, 실제 어댑터(capsule.llm.mode=real)는 도메인 단계에서 붙인다. */
public interface LlmClient {

    /** 이벤트 반응 말풍선 대사 (2줄 이내)와 감정. intensity는 AWAY일 때만 있음 */
    LlmReaction reaction(CharacterInfo character, String trigger, Integer intensity);

    LlmReply reply(ReplyContext context);

    /** 이번 세션 대화를 지난 요약에 합친 새 요약. previousSummary는 처음이면 null */
    String mergeSummary(String previousSummary, long sessionId, List<ChatLine> messages);

    String closingLine(String characterName);

    // TODO(judgment): J-04 trigger별 고정 대사 문구 (LLM이 5초 안에 못 만들 때도 이 표를 씀)
    Map<String, String> FIXED_REACTION_LINES = Map.of(
            "GREETING", "왔구나. 오늘도 같이 해볼까?",
            "AWAY", "잠깐 자리 비운 거야?",
            "RETURN", "왔네. 이어서 하자.",
            "BREAK", "잠깐 쉬자.",
            "BREAK_END", "다시 시작해볼까?");

    // TODO(judgment): J-05 답장이 5초를 넘을 때 쓸 대체 대사 문구
    String FALLBACK_REPLY = "미안, 잠깐 딴생각했어. 다시 말해줄래?";

    // TODO(judgment): J-09 완료 대사를 못 만들 때 쓸 문구 (지금은 명세 22 응답 예시 문장)
    String FALLBACK_CLOSING_LINE = "오늘도 같이 공부해서 좋았어. 다음에도 함께하자.";
}
