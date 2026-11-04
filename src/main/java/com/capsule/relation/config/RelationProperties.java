package com.capsule.relation.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 호감도 · 레벨 숫자 (2026-11-04 최종안, ADR 0021). PM 답이 오면 값만 바꾼다. 날짜는 한국 날짜 기준.
 * 대화 한 번의 점수(말의 종류별)는 캐릭터마다 달라서 dialogue가 정하고, 여기는 모든 캐릭터에 같은 규칙만 둔다.
 *
 * @param chatDailyGainCap 대화로 하루에 오를 수 있는 최대 (내려가는 쪽은 상한 없음)
 * @param awayMinSeconds 이보다 길게 비워야 감점
 * @param awayPenalty 자리 비움 한 번의 감점 (음수)
 * @param awayDailyCap 자리 비움으로 하루에 깎일 수 있는 최대 (양수)
 * @param streakPoints 연속 출석 2일째부터 하루 한 번 (그날 처음 공부를 시작하면)
 * @param levelSeconds 레벨 1이 오르는 데 필요한 자리 지킴 시간
 * @param stages 관계 단계: 호감도 min 이상이면 그 단계 (min 오름차순, 첫 단계 min은 0)
 */
@ConfigurationProperties("capsule.relation")
public record RelationProperties(
        int chatDailyGainCap,
        int awayMinSeconds,
        int awayPenalty,
        int awayDailyCap,
        int streakPoints,
        int levelSeconds,
        List<Stage> stages) {

    public record Stage(int min, String name) {}
}
