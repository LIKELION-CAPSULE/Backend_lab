package com.capsule.relation.service;

import static com.capsule.relation.api.RelationTypes.MAX_POINTS;
import static com.capsule.relation.api.RelationTypes.MIN_POINTS;

import com.capsule.common.settings.RuntimeSettings;
import com.capsule.relation.api.RelationTypes.ChatScore;
import com.capsule.relation.config.RelationProperties;
import org.springframework.stereotype.Component;

/**
 * 호감도 · 단계 · 레벨 공식 (호감도 최종안, ADR 0022). 숫자는 RelationProperties(application.yml)에 있고,
 * 실험실 '운영 설정'에서 바꾼 값이 있으면 그것을 쓴다 (ADR 0026). 범위 검사는 RelationSettingsConfig.check.
 * 호감도는 오르기 어렵고 내려가기 쉽다: 오르는 쪽만 하루 상한, 심한 말은 지금 값의 %로 깎는다.
 */
@Component
class AffinityPolicy {

    private final RuntimeSettings<RelationProperties> settings;

    AffinityPolicy(RuntimeSettings<RelationProperties> settings) {
        this.settings = settings;
    }

    /** 지금 쓰는 숫자. 한 요청 안에서 여러 번 불러도 된다 (메모리 사본) */
    RelationProperties props() {
        return settings.current();
    }

    static int clamp(int points) {
        return Math.clamp(points, MIN_POINTS, MAX_POINTS);
    }

    /** 대화 한 번에 오를 수 있는 최대. 설정이 아니라 상수: DB CHECK relation_logs_chat_range(−100~5, V20261104_165439)와 같음 */
    static final int CHAT_MAX_GAIN = 5;

    /**
     * 자르기 전 변화량. 비율 감점이면 지금 값의 %를 올림해서 깎되, 최소 감점(points)보다 덜 깎지 않는다.
     * 오르는 쪽은 설정에 큰 값이 들어와도 CHAT_MAX_GAIN까지만 (DB CHECK에 걸려 답장 저장이 실패하지 않게)
     */
    static int chatDelta(int current, ChatScore score) {
        if (score.lossPercent() <= 0) return Math.min(score.points(), CHAT_MAX_GAIN);
        int loss = (current * score.lossPercent() + 99) / 100;
        return Math.min(score.points(), -loss);
    }

    /** 관계 단계 (1부터): 호감도가 그 단계 min 이상인 마지막 단계. 호감도가 내려가면 단계도 내려간다 */
    int stageOf(int points) {
        var stages = props().stages();
        int stage = 0;
        while (stage < stages.size() && points >= stages.get(stage).min()) stage++;
        return stage;
    }

    /** 단계가 줄었는데 옛 단계 번호가 오면 마지막 단계 이름 (운영 설정에서 단계 수를 바꾼 직후) */
    String stageName(int stage) {
        var stages = props().stages();
        return stages.get(Math.min(stage, stages.size()) - 1).name();
    }

    // TODO(judgment): J-03 레벨 공식 (PM 임시: 자리 지킴 60분당 1레벨, 상한 없음)
    int levelOf(int totalPresentSeconds) {
        return 1 + totalPresentSeconds / props().levelSeconds();
    }

    /** 다음 레벨이 되는 누적 자리 지킴 시간 */
    int nextLevelSeconds(int level) {
        return level * props().levelSeconds();
    }
}
