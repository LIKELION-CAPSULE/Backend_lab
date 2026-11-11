package com.capsule.relation.config;

import static com.capsule.common.settings.RuntimeSettings.require;

import com.capsule.common.settings.RuntimeSettings;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/** 호감도 숫자를 실험실 '운영 설정'에서 바꾸게 (ADR 0026). 전부 매번 읽는 값이라 모든 칸을 연다 */
@Configuration
public class RelationSettingsConfig {

    @Bean
    public RuntimeSettings<RelationProperties> relationSettings(
            JdbcTemplate jdbc, JsonMapper json, RelationProperties yml) {
        return RuntimeSettings.stored(
                jdbc,
                json,
                "relation_settings",
                "relation",
                yml,
                RelationProperties.class,
                RuntimeSettings.allExcept(RelationProperties.class),
                RelationSettingsConfig::check);
    }

    /** 공식(AffinityPolicy)이 나누기 · 단계 고르기에서 깨지지 않을 범위 */
    public static void check(RelationProperties v) {
        require(v.chatDailyGainCap() >= 0, "chatDailyGainCap", "0 이상");
        require(v.awayMinSeconds() >= 0, "awayMinSeconds", "0 이상");
        require(v.awayPenalty() <= 0, "awayPenalty", "0 이하 (감점)");
        require(v.awayDailyCap() >= 0, "awayDailyCap", "0 이상");
        require(v.streakPoints() >= 0, "streakPoints", "0 이상");
        require(v.levelSeconds() > 0, "levelSeconds", "1 이상");
        List<RelationProperties.Stage> stages = v.stages();
        require(stages != null && !stages.isEmpty() && stages.getFirst().min() == 0, "stages", "첫 단계 min은 0");
        for (int i = 1; i < stages.size(); i++)
            require(
                    stages.get(i).min() > stages.get(i - 1).min()
                            && stages.get(i).min() <= 100,
                    "stages",
                    "min은 오름차순, 100 이하");
        require(stages.stream().allMatch(s -> s.name() != null && !s.name().isBlank()), "stages", "단계 이름이 비었음");
    }
}
