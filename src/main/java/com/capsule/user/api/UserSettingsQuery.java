package com.capsule.user.api;

import com.capsule.user.api.UserTypes.SettingsInfo;

/** 설정 조회 (api.md 29와 같은 값). session의 연결 시작(16 settings)이 쓴다 (2026-11-23) */
public interface UserSettingsQuery {

    /** 저장한 적 없는 키는 기본값으로 채운 설정 7개. 탈퇴했거나 없는 사용자면 401 (29와 같음) */
    SettingsInfo settings(long userId);
}
