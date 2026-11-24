package com.capsule.content.api;

import com.capsule.content.api.ContentTypes.VoiceInfo;
import java.util.Optional;

/** 메이트 목소리 (api.md 28 '음성', 2026-11-25, ADR 0036). dialogue의 대화 음성이 쓴다 */
public interface VoiceQuery {

    /** 이 메이트의 목소리. 없는 메이트면 빈 값, 있지만 안 정했으면 칸이 null인 값 */
    Optional<VoiceInfo> voice(long mateId);
}
