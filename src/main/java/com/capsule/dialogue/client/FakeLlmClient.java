package com.capsule.dialogue.client;

import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.dialogue.dto.ChatLine;
import com.capsule.dialogue.dto.LlmReaction;
import com.capsule.dialogue.dto.LlmReply;
import com.capsule.dialogue.dto.ReplyContext;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 결정적인 가짜 LLM (같은 입력이면 항상 같은 값). E2E가 응답을 검증할 수 있게 */
@Component
@ConditionalOnProperty(name = "capsule.llm.mode", havingValue = "fake", matchIfMissing = true)
class FakeLlmClient implements LlmClient {

    @Override
    public LlmReaction reaction(CharacterInfo character, String trigger, Integer intensity) {
        // TODO(judgment): J-04 이벤트 반응 대사를 LLM으로 생성 (캐릭터 말투·intensity 반영). 지금은 trigger별 고정 대사
        // TODO(judgment): J-12 반응 감정(emotion) 값 목록과 정하는 기준. 지금은 trigger별 고정 값
        return new LlmReaction(FIXED_REACTION_LINES.get(trigger), FAKE_EMOTIONS.get(trigger));
    }

    private static final Map<String, String> FAKE_EMOTIONS = Map.of(
            "GREETING", "HAPPY",
            "AWAY", "SAD",
            "RETURN", "HAPPY",
            "BREAK", "NEUTRAL",
            "BREAK_END", "NEUTRAL");

    @Override
    public LlmReply reply(ReplyContext context) {
        String head = context.userMessage()
                .codePoints()
                .limit(20)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        // TODO(judgment): J-05 답장 생성 (캐릭터 설정·요약·이번 대화·일정 반영, 길이: 공부 중 1~2문장·쉬는 시간 2~4문장). 지금은 고정 문장 + 입력 앞 20자
        // TODO(judgment): J-06 대화 호감도 판정 (−5~5). 지금은 항상 +1
        return new LlmReply("「" + head + "」… 그렇구나. 한 문제만 더 해보자.", null, 1);
    }

    @Override
    public String mergeSummary(String previousSummary, long sessionId, List<ChatLine> messages) {
        // TODO(judgment): J-08 세션 대화를 요약에 합치는 방법. 지금은 "메시지 n개" 한 줄만 덧붙임
        String line = "[세션 " + sessionId + "] 메시지 " + messages.size() + "개";
        return previousSummary == null ? line : previousSummary + "\n" + line;
    }

    @Override
    public String closingLine(String characterName) {
        // TODO(judgment): J-09 완료 대사 생성. 지금은 캐릭터 이름을 넣은 고정 문구
        return characterName + ": 오늘도 같이 공부해서 좋았어. 다음에도 함께하자.";
    }
}
