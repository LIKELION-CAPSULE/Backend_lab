package com.capsule.relation.controller;

import com.capsule.relation.dto.RelationAdjustRequest;
import com.capsule.relation.dto.RelationLabView;
import com.capsule.relation.service.RelationLab;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 흐름 테스트(/internal/llm-lab/flow)의 호감도 상태판. 명세 밖 내부 API라 Swagger에서 뺀다.
 * 실험실 보안 체인(dialogue LabSecurityConfig: 이 컴퓨터에서 온 요청 또는 X-Lab-Token)이 이 경로도 막는다.
 */
@Hidden
@RestController
@RequestMapping("/internal/llm-lab/api/relations")
@ConditionalOnProperty(name = "capsule.llm.lab.enabled", havingValue = "true")
@RequiredArgsConstructor
public class RelationLabController {

    private final RelationLab lab;

    @GetMapping
    public RelationLabView get(@RequestParam long userId, @RequestParam long characterId) {
        return lab.view(userId, characterId);
    }

    @PutMapping
    public RelationLabView adjust(
            @RequestParam long userId, @RequestParam long characterId, @RequestBody RelationAdjustRequest body) {
        Integer presentSeconds = body.presentMinutes() == null ? null : body.presentMinutes() * 60;
        return lab.adjust(userId, characterId, body.points(), presentSeconds, body.sessionId());
    }
}
