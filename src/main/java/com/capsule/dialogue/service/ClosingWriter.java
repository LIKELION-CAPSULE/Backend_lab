package com.capsule.dialogue.service;

import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.dialogue.client.Llm;
import com.capsule.dialogue.client.LlmGateway;
import com.capsule.dialogue.domain.PromptKey;
import com.capsule.dialogue.domain.PromptVersion;
import com.capsule.dialogue.dto.ChatLine;
import com.capsule.dialogue.dto.LlmContext;
import com.capsule.dialogue.dto.LlmPurpose;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 공부 종료(명세 22)에서 LLM이 쓰는 글: 이번 세션 흐름 카드, 완료 대사. 시간 제한(5초)은 부르는 쪽이 지킨다 */
@Component
public class ClosingWriter {

    /** 카드 본문 길이. 설정이 아니라 상수: DB CHECK conversation_recaps.content 1~200자, 머리말 포함 (V20261104_204409) */
    static final int RECAP_MAX = 150;

    private final LlmGateway llm;
    private final PromptService prompts;

    ClosingWriter(LlmGateway llm, PromptService prompts) {
        this.llm = llm;
        this.prompts = prompts;
    }

    /**
     * 세션 하나를 흐름 카드 한 장으로 (기억 ②, ADR 0022). 지난 카드를 다시 압축하지 않는다(재귀 요약은 쓸수록 정보가 빠짐).
     * 오래 남길 사실은 MemoryKeeper가 매 턴 이미 뽑았으므로, 카드는 흐름만 쓰고 오래되면 지워져도 된다.
     *
     * @param header 코드가 붙이는 머리말 (예: "11/03 52분")
     * @param facts 캐릭터가 이미 아는 사실 (카드에 되풀이하지 않게)
     */
    // TODO(judgment): J-08 카드 길이 · 내용 (임시: 70자 안팎 한두 문장, summary.system 프롬프트)
    public String recap(
            CharacterInfo character,
            String nickname,
            String header,
            List<String> facts,
            List<ChatLine> lines,
            Map<String, Long> pinned,
            LlmContext ctx) {
        PromptVersion system = prompts.resolve(PromptKey.SUMMARY_SYSTEM, character.id(), pinned);
        Map<String, String> vars = PromptVars.character(character);
        vars.put("nickname", nickname);
        String input = "<아는 사실>\n" + (facts.isEmpty() ? "없음" : "- " + String.join("\n- ", facts))
                + "\n</아는 사실>\n\n<이번 대화>\n" + PromptVars.transcript(lines, character.name()) + "\n</이번 대화>";
        Llm.Result r = llm.complete(
                LlmPurpose.SUMMARY,
                List.of(new Llm.SystemBlock(PromptService.render(system.body(), vars), false)),
                List.of(new Llm.ChatTurn("user", input)),
                ctx.withPrompts(List.of(system.id())),
                null);
        String body = r.text().strip().replaceAll("\\s+", " ");
        if (body.isEmpty()) throw new IllegalStateException("빈 카드");
        if (body.length() > RECAP_MAX) body = body.substring(0, RECAP_MAX);
        return header + ", " + body;
    }

    // TODO(judgment): J-09 완료 대사에 넣을 정보(목표 달성·레벨업 등). 지금은 공부 시간과 끝낸 방법만
    public String closingLine(
            CharacterInfo character,
            String nickname,
            int studySeconds,
            String endType,
            Map<String, Long> pinned,
            LlmContext ctx) {
        PromptVersion system = prompts.resolve(PromptKey.CLOSING_SYSTEM, character.id(), pinned);
        Map<String, String> vars = PromptVars.character(character);
        vars.put("nickname", nickname);
        vars.put(
                "result",
                "공부 시간: " + studySeconds / 60 + "분\n끝낸 방법: "
                        + ("AUTO_CLOSED".equals(endType) ? "오래 켜 둬서 자동으로 끝남" : "직접 끝냄"));
        Llm.Result r = llm.complete(
                LlmPurpose.CLOSING,
                List.of(new Llm.SystemBlock(PromptService.render(system.body(), vars), false)),
                List.of(new Llm.ChatTurn("user", "완료 대사를 써 줘.")),
                ctx.withPrompts(List.of(system.id())),
                null);
        String line = r.text().strip().replaceAll("^[\"“]|[\"”]$", "").strip();
        if (line.isEmpty()) throw new IllegalStateException("빈 완료 대사");
        return line;
    }
}
