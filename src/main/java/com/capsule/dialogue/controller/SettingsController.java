package com.capsule.dialogue.controller;

import com.capsule.common.error.ApiException;
import com.capsule.common.settings.RuntimeSettings;
import com.capsule.dialogue.config.LabAdmins;
import com.capsule.dialogue.config.LlmProperties;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
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
public class SettingsController {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    private final Map<String, RuntimeSettings<?>> sections = new LinkedHashMap<>();
    /** 공용 개발 DB에 인증 없이 붙은 노트북(run-local.sh --shared): 그 행은 개발 서버가 읽으므로 저장을 막는다 */
    private final boolean readOnly;

    public SettingsController(List<RuntimeSettings<?>> all, Environment env, LlmProperties llm, LabAdmins admins) {
        for (RuntimeSettings<?> s : all)
            if (sections.put(s.section(), s) != null) throw new IllegalStateException("운영 설정 이름이 겹침: " + s.section());
        String token = llm.lab().token();
        this.readOnly = env.matchesProfiles("shared-db") && (token == null || token.isBlank()) && !admins.enabled();
    }

    @GetMapping
    public List<RuntimeSettings.View> all() {
        return sections.values().stream().map(RuntimeSettings::view).toList();
    }

    /** 덮어쓸 칸 전체 {칸: 값}. 빠진 칸은 yml 값으로 돌아간다 ({}면 전부 되돌리기) */
    @PutMapping("/{section}")
    public RuntimeSettings.View save(
            @PathVariable String section,
            @RequestBody JsonNode overrides,
            @RequestAttribute(name = LabAdmins.REQUEST_ATTRIBUTE, required = false) String admin) {
        RuntimeSettings<?> s = sections.get(section);
        if (s == null) throw new ApiException(HttpStatus.NOT_FOUND, "SETTING_NOT_FOUND");
        if (readOnly) throw new ApiException(HttpStatus.FORBIDDEN, "SETTINGS_READ_ONLY");
        RuntimeSettings.View view = s.update(overrides);
        log.info("운영 설정 {}을 바꾼 사람: {}", section, admin != null ? admin : "(고정 토큰 · 이 컴퓨터)");
        return view;
    }
}
