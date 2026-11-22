package com.capsule.content.api;

import com.capsule.content.api.ContentTypes.MateInfo;
import java.util.Optional;

/** 메이트(= 캐릭터) 화면 정보 (api.md 31). 홈 · 연결 단계에서 session도 이 계약을 쓴다 */
public interface MateQuery {

    /** 비활성 메이트도 찾음 (active로 판단) */
    Optional<MateInfo> find(long mateId);
}
