package com.capsule.dialogue.controller;

import com.capsule.common.config.ApiTags;
import com.capsule.common.error.ApiException;
import com.capsule.common.error.ErrorResponse;
import com.capsule.common.security.LoginUser;
import com.capsule.dialogue.dto.MessageRequest;
import com.capsule.dialogue.dto.MessageResponse;
import com.capsule.dialogue.dto.ReactionRequest;
import com.capsule.dialogue.dto.ReactionResponse;
import com.capsule.dialogue.service.DialogueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sessions/{sessionId}")
@Tag(name = ApiTags.DIALOGUE)
class DialogueController {

    private static final Logger log = LoggerFactory.getLogger(DialogueController.class);
    private static final ExecutorService STREAMS = Executors.newVirtualThreadPerTaskExecutor();
    /** 첫 대사 llm-budget + 끝까지 LLM http-timeout(30초)보다 넉넉히. 프론트 프록시 상한(60초) 안 */
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(50);

    private final DialogueService dialogue;

    DialogueController(DialogueService dialogue) {
        this.dialogue = dialogue;
    }

    @Operation(summary = "19. 상태 반응 받기", description = "eventId로 캐릭터 대사(LLM) + 감정 + 감정에 맞는 모션 · 표정 받기 (명세: api.md 19)")
    @PostMapping("/events/{eventId}/reaction")
    ReactionResponse react(
            @LoginUser long userId,
            @PathVariable long sessionId,
            @PathVariable long eventId,
            @Valid @RequestBody ReactionRequest request) {
        return dialogue.react(userId, sessionId, eventId, request.requestId(), request.intensity(), request.tts());
    }

    @Operation(summary = "20. 메시지 전송", description = "대화 → 캐릭터별 답장 + 감정 모션 · 표정 + 바뀐 호감도 (TTS 선택) (명세: api.md 20)")
    @PostMapping("/messages")
    MessageResponse send(
            @LoginUser long userId, @PathVariable long sessionId, @Valid @RequestBody MessageRequest request) {
        return dialogue.send(userId, sessionId, request);
    }

    @Operation(
            summary = "28. 메시지 전송 (스트리밍)",
            description =
                    "20과 같은 대화를 SSE로: 답장 글이 생길 때마다 reply {content}, 끝나면 done (20 응답 전체), 스트림 도중 오류는 error (명세: api.md 28)")
    @PostMapping("/messages/stream")
    SseEmitter stream(
            @LoginUser long userId, @PathVariable long sessionId, @Valid @RequestBody MessageRequest request) {
        // 검증 · 재전송 · 속도 제한은 스트림을 열기 전에: 여기서 난 오류는 보통 JSON 에러 응답 (명세 28)
        DialogueService.Accepted accepted = dialogue.accept(userId, sessionId, request);
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        emitter.onTimeout(emitter::complete); // 이미 연 SSE에 에러 JSON을 쓰려 하지 않게
        STREAMS.execute(() -> {
            try {
                MessageResponse done =
                        dialogue.reply(accepted, text -> send(emitter, "reply", Map.of("content", text)));
                send(emitter, "done", done);
            } catch (ApiException e) {
                send(emitter, "error", new ErrorResponse(e.code(), e.code(), e.detail()));
            } catch (RuntimeException e) {
                log.error("스트리밍 답장 실패", e);
                send(emitter, "error", ErrorResponse.of("INTERNAL_ERROR"));
            } finally {
                emitter.complete();
            }
        });
        return emitter;
    }

    /** 사용자가 창을 닫아 연결이 끊겨도 답장은 끝까지 만들어 저장한다 (같은 requestId로 다시 보내면 받음) */
    private static void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            log.debug("스트림이 이미 닫힘 ({}): {}", event, e.toString());
        }
    }
}
