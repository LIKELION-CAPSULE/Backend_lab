package com.capsule.relation.service;

import static com.capsule.relation.api.RelationTypes.MAX_POINTS;
import static com.capsule.relation.api.RelationTypes.MIN_POINTS;

import com.capsule.relation.api.RelationTypes.ChatScore;
import com.capsule.relation.config.RelationProperties;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * 호감도 · 단계 · 레벨 공식 (호감도 최종안, ADR 0022). 숫자는 RelationProperties(application.yml)에 있다.
 * 호감도는 오르기 어렵고 내려가기 쉽다: 오르는 쪽만 하루 상한, 심한 말은 지금 값의 %로 깎는다.
 */
@Component
class AffinityPolicy {

    /** 하루를 나누는 날짜 기준 */
    static final ZoneId DAY_ZONE = ZoneId.of("Asia/Seoul");

    final RelationProperties props;

    AffinityPolicy(RelationProperties props) {
        if (props.stages().isEmpty() || props.stages().getFirst().min() != MIN_POINTS)
            throw new IllegalStateException("capsule.relation.stages의 첫 단계 min은 0이어야 함");
        this.props = props;
    }

    static int clamp(int points) {
        return Math.clamp(points, MIN_POINTS, MAX_POINTS);
    }

    /** 대화 한 번에 오를 수 있는 최대 (DB CHECK relation_logs_chat_range와 같음) */
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
        int stage = 0;
        while (stage < props.stages().size()
                && points >= props.stages().get(stage).min()) stage++;
        return stage;
    }

    String stageName(int stage) {
        return props.stages().get(stage - 1).name();
    }

    // TODO(judgment): J-03 레벨 공식 (PM 임시: 자리 지킴 60분당 1레벨, 상한 없음)
    int levelOf(int totalPresentSeconds) {
        return 1 + totalPresentSeconds / props.levelSeconds();
    }

    /** 다음 레벨이 되는 누적 자리 지킴 시간 */
    int nextLevelSeconds(int level) {
        return level * props.levelSeconds();
    }
}
