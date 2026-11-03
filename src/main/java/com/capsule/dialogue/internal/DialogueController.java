package com.capsule.dialogue.internal;

import com.capsule.common.security.LoginUser;
import com.capsule.dialogue.internal.DialogueService.MessageResponse;
import com.capsule.dialogue.internal.DialogueService.ReactionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions/{sessionId}")
class DialogueController {

    private final DialogueService dialogue;

    DialogueController(DialogueService dialogue) {
        this.dialogue = dialogue;
    }

    /** intensity는 AWAY일 때만 쓰고 다른 이벤트는 무시 (범위 밖이면 이벤트와 상관없이 400) */
    record ReactionRequest(
            @NotNull UUID requestId, @Min(1) @Max(3) Integer intensity, Boolean tts) {}

    /** inputType은 검증만 한다 (저장할 컬럼 없음) */
    enum InputType {
        TEXT,
        QUICK
    }

    record MessageRequest(
            @NotNull UUID requestId,

            @NotBlank(message = "EMPTY_MESSAGE") @Size(max = 300)
            String content,

            @NotNull InputType inputType,
            Boolean tts) {}

    @PostMapping("/events/{eventId}/reaction")
    ReactionResponse react(
            @LoginUser long userId,
            @PathVariable long sessionId,
            @PathVariable long eventId,
            @Valid @RequestBody ReactionRequest request) {
        return dialogue.react(userId, sessionId, eventId, request.requestId(), request.intensity(), request.tts());
    }

    @PostMapping("/messages")
    MessageResponse send(
            @LoginUser long userId, @PathVariable long sessionId, @Valid @RequestBody MessageRequest request) {
        return dialogue.send(userId, sessionId, request.requestId(), request.content(), request.tts());
    }
}
