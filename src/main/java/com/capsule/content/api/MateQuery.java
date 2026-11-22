package com.capsule.content.api;

import com.capsule.content.api.ContentTypes.MateInfo;
import java.util.List;
import java.util.Optional;

/** 메이트(= 캐릭터) 화면 정보 (api.md 11 · 31). session의 홈 · 연결 시작도 이 계약을 쓴다 */
public interface MateQuery {

    /** 비활성 메이트도 찾음 (active로 판단) */
    Optional<MateInfo> find(long mateId);

    /** 홈 메이트 리스트: 활성 메이트, 정렬 순서 (준비 중 포함) */
    List<MateInfo> activeMates();
}
