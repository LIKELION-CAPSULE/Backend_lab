package com.capsule.dialogue.controller;

import com.capsule.common.error.ApiException;
import com.capsule.common.settings.RuntimeSettings;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * 실험실 '운영 설정' (ADR 0026): 호감도(relation) · 대화(dialogue) · AI 공급자와 모델(llm)을 배포 없이 바꾼다.
 * 묶음은 모듈마다 RuntimeSettings 빈으로 둔 것을 다 모은다 (relation 것도 타입으로만 받아서 그 모듈 코드를 부르지 않음).
 * 보안은 실험실과 같다 (LabSecurityConfig). 프롬프트는 /prompts, 캐릭터 설정은 /characters로 이미 바꿀 수 있다
 */
@Hidden // 내부 도구: 프론트용 Swagger에 넣지 않는다 (다른 실험실 API와 같음)
@RestController
@RequestMapping("/internal/llm-lab/api/settings")
@ConditionalOnProperty(name = "capsule.llm.lab.enabled", havingValue = "true")
class SettingsController {

    private final Map<String, RuntimeSettings<?>> sections = new LinkedHashMap<>();

    SettingsController(List<RuntimeSettings<?>> all) {
        for (RuntimeSettings<?> s : all)
            if (sections.put(s.section(), s) != null) throw new IllegalStateException("운영 설정 이름이 겹침: " + s.section());
    }

    @GetMapping
    List<RuntimeSettings.View> all() {
        return sections.values().stream().map(RuntimeSettings::view).toList();
    }

    /** 덮어쓸 칸 전체 {칸: 값}. 빠진 칸은 yml 값으로 돌아간다 ({}면 전부 되돌리기) */
    @PutMapping("/{section}")
    RuntimeSettings.View save(@PathVariable String section, @RequestBody JsonNode overrides) {
        RuntimeSettings<?> s = sections.get(section);
        if (s == null) throw new ApiException(HttpStatus.NOT_FOUND, "SETTING_NOT_FOUND");
        return s.update(overrides);
    }
}
