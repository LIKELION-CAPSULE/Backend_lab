package com.capsule.dialogue.controller;

import com.capsule.common.error.ApiException;
import com.capsule.dialogue.domain.PromptKey;
import com.capsule.dialogue.dto.EnginePackRequest;
import com.capsule.dialogue.dto.EvalCaseRequest;
import com.capsule.dialogue.dto.EvalCaseView;
import com.capsule.dialogue.dto.EvalResultView;
import com.capsule.dialogue.dto.EvalRunRequest;
import com.capsule.dialogue.dto.EvalRunView;
import com.capsule.dialogue.dto.LabMeta;
import com.capsule.dialogue.dto.LabSessionRequest;
import com.capsule.dialogue.dto.LabSessionView;
import com.capsule.dialogue.dto.LabTurnRequest;
import com.capsule.dialogue.dto.LabTurnView;
import com.capsule.dialogue.dto.PlaygroundRequest;
import com.capsule.dialogue.dto.PlaygroundResult;
import com.capsule.dialogue.dto.PromptRequest;
import com.capsule.dialogue.dto.PromptView;
import com.capsule.dialogue.dto.RatingRequest;
import com.capsule.dialogue.dto.UsageSummary;
import com.capsule.dialogue.dto.UsageView;
import com.capsule.dialogue.service.EvalService;
import com.capsule.dialogue.service.LabService;
import com.capsule.dialogue.service.PromptService;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * LLM 실험실 (내부 품질 테스트, ADR 0020). capsule.llm.lab.enabled=true일 때만 생긴다 (local 기본 켬, 운영 끔).
 * 화면: GET /internal/llm-lab. 요청 예시: http/llm-lab.http
 */
@RestController
@RequestMapping("/internal/llm-lab")
@ConditionalOnProperty(name = "capsule.llm.lab.enabled", havingValue = "true")
class LlmLabController {

    private final LabService lab;
    private final EvalService eval;
    private final PromptService prompts;

    LlmLabController(LabService lab, EvalService eval, PromptService prompts) {
        this.lab = lab;
        this.eval = eval;
        this.prompts = prompts;
    }

    @GetMapping({"", "/"})
    ResponseEntity<Resource> page() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(new ClassPathResource("llm-lab/index.html"));
    }

    @GetMapping("/api/meta")
    LabMeta meta() {
        return lab.meta();
    }

    // --- 프롬프트 버전 ---

    @GetMapping("/api/prompts")
    List<PromptView> prompts(
            @RequestParam(required = false) String key, @RequestParam(required = false) Long characterId) {
        return prompts.search(key, characterId).stream().map(PromptView::from).toList();
    }

    @PostMapping("/api/prompts")
    PromptView createPrompt(@Valid @RequestBody PromptRequest r) {
        return PromptView.from(prompts.create(key(r.key()), r.characterId(), r.body(), r.note(), r.activate()));
    }

    @PostMapping("/api/prompts/{id}/activate")
    PromptView activate(@PathVariable long id) {
        return PromptView.from(prompts.activate(id));
    }

    @GetMapping("/api/prompts/active")
    Map<String, PromptView> active(@RequestParam long characterId) {
        Map<String, PromptView> set = new LinkedHashMap<>();
        prompts.activeSet(characterId).forEach((k, v) -> set.put(k, PromptView.from(v)));
        return set;
    }

    @PostMapping("/api/prompts/engine-pack")
    List<PromptView> enginePack(@Valid @RequestBody EnginePackRequest r) {
        return prompts
                .importEnginePack(
                        r.characterId(), r.characterYml(), r.actor(), r.director(), r.judge(), r.note(), r.activate())
                .stream()
                .map(PromptView::from)
                .toList();
    }

    // --- 실험 대화방 ---

    @GetMapping("/api/sessions")
    List<LabSessionView> sessions() {
        return lab.sessions();
    }

    @PostMapping("/api/sessions")
    LabSessionView createSession(@RequestBody LabSessionRequest r) {
        return lab.create(r);
    }

    @GetMapping("/api/sessions/{id}")
    LabSessionView session(@PathVariable long id) {
        return lab.session(id);
    }

    @PatchMapping("/api/sessions/{id}")
    LabSessionView updateSession(@PathVariable long id, @RequestBody LabSessionRequest r) {
        return lab.update(id, r);
    }

    @PostMapping("/api/sessions/{id}/reset")
    LabSessionView reset(@PathVariable long id) {
        return lab.reset(id);
    }

    @DeleteMapping("/api/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteSession(@PathVariable long id) {
        lab.delete(id);
    }

    @PostMapping("/api/sessions/{id}/turns")
    LabTurnView turn(@PathVariable long id, @Valid @RequestBody LabTurnRequest r) {
        return lab.turn(id, r.text());
    }

    @PatchMapping("/api/turns/{id}")
    LabTurnView rateTurn(@PathVariable long id, @Valid @RequestBody RatingRequest r) {
        return lab.rate(id, r);
    }

    // --- 한 번 실행 ---

    @PostMapping("/api/playground")
    List<PlaygroundResult> playground(@Valid @RequestBody PlaygroundRequest r) {
        return lab.playground(r);
    }

    // --- 평가 ---

    @GetMapping("/api/eval/cases")
    List<EvalCaseView> cases() {
        return eval.cases();
    }

    @PostMapping("/api/eval/cases")
    EvalCaseView createCase(@Valid @RequestBody EvalCaseRequest r) {
        return eval.createCase(r);
    }

    @PutMapping("/api/eval/cases/{id}")
    EvalCaseView updateCase(@PathVariable long id, @Valid @RequestBody EvalCaseRequest r) {
        return eval.updateCase(id, r);
    }

    @DeleteMapping("/api/eval/cases/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteCase(@PathVariable long id) {
        eval.deleteCase(id);
    }

    @PostMapping("/api/eval/runs")
    EvalRunView startRun(@Valid @RequestBody EvalRunRequest r) {
        return eval.start(r);
    }

    @GetMapping("/api/eval/runs")
    List<EvalRunView> runs() {
        return eval.runs();
    }

    @GetMapping("/api/eval/runs/{id}")
    EvalRunView run(@PathVariable long id) {
        return eval.run(id);
    }

    @PatchMapping("/api/eval/results/{id}")
    EvalResultView rateResult(@PathVariable long id, @Valid @RequestBody RatingRequest r) {
        return eval.rate(id, r);
    }

    // --- 호출 기록 ---

    @GetMapping("/api/usages")
    List<UsageView> usages() {
        return lab.recentUsages();
    }

    @GetMapping("/api/usages/summary")
    List<UsageSummary> usageSummary(@RequestParam(defaultValue = "7") int days) {
        return lab.usageSummary(days);
    }

    @GetMapping("/api/usages/trace/{traceId}")
    List<UsageView> trace(@PathVariable UUID traceId) {
        return lab.trace(traceId);
    }

    private static PromptKey key(String key) {
        try {
            return PromptKey.of(key);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_PROMPT_KEY");
        }
    }
}
