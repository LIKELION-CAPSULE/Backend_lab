package com.capsule.user.adapter;

import com.capsule.user.api.UserSettingsQuery;
import com.capsule.user.api.UserTypes.SettingsInfo;
import com.capsule.user.dto.UserSettings;
import com.capsule.user.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** UserSettingsQuery 계약 구현 (UserQueryAdapter와 같은 이유로 얇게 따로 둔다). 기본값 · 검사는 SettingsService 한곳 */
@Component
@RequiredArgsConstructor
public class UserSettingsQueryAdapter implements UserSettingsQuery {

    private final SettingsService settings;

    @Override
    public SettingsInfo settings(long userId) {
        UserSettings s = settings.get(userId);
        return new SettingsInfo(
                s.talkFrequency(),
                s.voiceEnabled(),
                s.voiceVolume(),
                s.voiceSpeed(),
                s.pipBubble(),
                s.defaultMode(),
                s.pipSize());
    }
}
