package com.capsule.dialogue.service;

import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.dialogue.client.LlmGateway;
import com.capsule.dialogue.domain.PromptKey;
import com.capsule.dialogue.domain.PromptVersion;
import com.capsule.dialogue.dto.ChatLine;
import com.capsule.dialogue.dto.LlmContext;
import com.capsule.dialogue.dto.LlmPurpose;
import com.capsule.dialogue.engine.Llm;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 공부 종료(명세 22)에서 LLM이 쓰는 글: 지난 요약에 이번 대화 합치기, 완료 대사. 시간 제한(5초)은 부르는 쪽이 지킨다 */
@Component
public class ClosingWriter {

    private final LlmGateway llm;
    private final PromptService prompts;

    ClosingWriter(LlmGateway llm, PromptService prompts) {
        this.llm = llm;
        this.prompts = prompts;
    }

    // TODO(judgment): J-08 요약 방법(합치기·다시 쓰기, 길이). 지금은 summary.system 프롬프트의 기본안
    public String mergeSummary(
            CharacterInfo character,
            String nickname,
            String previousSummary,
            List<ChatLine> lines,
            Map<String, Long> pinned,
            LlmContext ctx) {
        PromptVersion system = prompts.resolve(PromptKey.SUMMARY_SYSTEM, character.id(), pinned);
        Map<String, String> vars = PromptVars.character(character);
        vars.put("nickname", nickname);
        String input = "<이전 요약>\n" + PromptVars.orNone(previousSummary, "없음") + "\n</이전 요약>\n\n<이번 대화>\n"
                + PromptVars.transcript(lines, character.name()) + "\n</이번 대화>";
        Llm.Result r = llm.complete(
                LlmPurpose.SUMMARY,
                List.of(new Llm.SystemBlock(PromptService.render(system.body(), vars), false)),
                List.of(new Llm.ChatTurn("user", input)),
                ctx.withPrompts(List.of(system.id())),
                null);
        String summary = r.text().strip();
        if (summary.isEmpty()) throw new IllegalStateException("빈 요약");
        return summary;
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
