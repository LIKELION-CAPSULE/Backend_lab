package com.capsule.user.service;

import com.capsule.common.error.ApiException;
import com.capsule.common.error.CommonErrorCode;
import com.capsule.user.domain.UserErrorCode;
import com.capsule.user.dto.UserSettings;
import com.capsule.user.repository.UserRepository;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 설정 조회 · 변경 (api.md 29 · 30). users.settings(jsonb)에는 사용자가 바꾼 키만 저장하고, 기본값과 검사는 여기 한곳에 둔다.
 * 기본값을 저장하지 않아서, 나중에 기본값을 바꾸면 손대지 않은 사람은 새 기본값을 받는다
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final Set<String> TALK_FREQUENCY = Set.of("QUIET", "SOMETIMES", "OFTEN");
    private static final Set<String> VOICE_SPEED = Set.of("SLOW", "NORMAL", "FAST");
    private static final Set<String> MODE = Set.of("BASIC", "ADVANCED");
    private static final Set<String> PIP_SIZE = Set.of("S", "M", "L");

    /** 키 → (기본값, 허용 검사) */
    private static final Map<String, Rule> RULES = Map.of(
            "talkFrequency", new Rule("SOMETIMES", oneOf(TALK_FREQUENCY)),
            "voiceEnabled", new Rule(true, JsonNode::isBoolean),
            "voiceVolume", new Rule(70, v -> v.isInt() && v.intValue() >= 0 && v.intValue() <= 100),
            "voiceSpeed", new Rule("NORMAL", oneOf(VOICE_SPEED)),
            "pipBubble", new Rule(true, JsonNode::isBoolean),
            "defaultMode", new Rule("BASIC", oneOf(MODE)),
            "pipSize", new Rule("M", oneOf(PIP_SIZE)));

    private record Rule(Object defaultValue, Predicate<JsonNode> valid) {}

    private final UserRepository users;
    private final JsonMapper json;

    @Transactional(readOnly = true)
    public UserSettings get(long userId) {
        return toSettings(stored(users.findSettings(userId)));
    }

    /** 보낸 키만 바꾼다. null이면 그 키를 지워 기본값으로. 하나라도 틀리면 아무것도 저장하지 않음 */
    @Transactional
    public UserSettings update(long userId, JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new ApiException(UserErrorCode.INVALID_SETTINGS);
        }
        ObjectNode settings = stored(users.findSettingsForUpdate(userId));
        for (Map.Entry<String, JsonNode> e : body.properties()) {
            Rule rule = RULES.get(e.getKey());
            if (rule == null) {
                throw new ApiException(UserErrorCode.INVALID_SETTINGS);
            }
            if (e.getValue().isNull()) {
                settings.remove(e.getKey());
            } else if (rule.valid().test(e.getValue())) {
                settings.set(e.getKey(), e.getValue());
            } else {
                throw new ApiException(UserErrorCode.INVALID_SETTINGS);
            }
        }
        users.updateSettings(userId, json.writeValueAsString(settings));
        return toSettings(settings);
    }

    /** 비었으면 = 탈퇴했거나 없는 사용자 → 401 (내 정보 조회와 같게) */
    private ObjectNode stored(Optional<String> row) {
        String raw = row.orElseThrow(() -> new ApiException(CommonErrorCode.UNAUTHORIZED));
        // 저장된 값 중 지금 규칙에 맞지 않는 키(이름이 바뀐 키 등)는 버린다: 조회가 400이 되지 않게
        ObjectNode settings = json.createObjectNode();
        for (Map.Entry<String, JsonNode> e : json.readTree(raw).properties()) {
            Rule rule = RULES.get(e.getKey());
            if (rule != null && rule.valid().test(e.getValue())) {
                settings.set(e.getKey(), e.getValue());
            }
        }
        return settings;
    }

    private UserSettings toSettings(ObjectNode stored) {
        ObjectNode full = json.createObjectNode();
        RULES.forEach((key, rule) -> full.set(key, json.valueToTree(rule.defaultValue())));
        full.setAll(stored);
        return json.treeToValue(full, UserSettings.class);
    }

    private static Predicate<JsonNode> oneOf(Set<String> values) {
        return v -> v.isString() && values.contains(v.stringValue());
    }
}
