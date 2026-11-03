package com.capsule.dialogue.controller;

import com.capsule.common.security.LoginUser;
import com.capsule.dialogue.dto.MessageRequest;
import com.capsule.dialogue.dto.MessageResponse;
import com.capsule.dialogue.dto.ReactionRequest;
import com.capsule.dialogue.dto.ReactionResponse;
import com.capsule.dialogue.service.DialogueService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
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
        try {
            return dialogue.send(
                    userId, sessionId, request.requestId(), request.characterId(), request.content(), request.tts());
        } catch (DataIntegrityViolationException e) {
            // 같은 requestId 동시 요청: 늦은 쪽은 UNIQUE에 막힌다 → 먼저 온 요청이 저장한 답장을 돌려줌
            return dialogue.answeredAfterConflict(
                            userId, sessionId, request.requestId(), request.characterId(), request.tts())
                    .orElseThrow(() -> e);
        }
    }
}
