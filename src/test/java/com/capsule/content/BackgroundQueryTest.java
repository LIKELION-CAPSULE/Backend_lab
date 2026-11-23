package com.capsule.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capsule.TestcontainersConfiguration;
import com.capsule.content.api.BackgroundQuery;
import com.capsule.content.api.ContentTypes.BackgroundInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 메이트 배경 조회 (ADR 0032): 활성만 정렬 순서로, 이미지는 CDN 주소를 붙여서. 시드 메이트를 건드리지 않게 테스트 전용 메이트로 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class BackgroundQueryTest {

    @Autowired
    BackgroundQuery backgrounds;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void activeBackgroundsInOrderWithDefaultAsCurrent() {
        long mate = newMate();
        assertThat(backgrounds.backgrounds(mate)).isEmpty();
        assertThat(backgrounds.current(mate, null)).isEmpty();

        jdbc.update(
                "INSERT INTO character_backgrounds (character_id, code, name, image_url, guide, is_default, sort_order)"
                        + " VALUES (?, 'cafe', '카페', 'bg/cafe.png', '카페에 갔을 때', false, 2),"
                        + " (?, 'classroom', '교실', 'https://other.cdn/classroom.png', null, true, 1),"
                        + " (?, 'old', '안 씀', 'bg/old.png', null, false, 0)",
                mate,
                mate,
                mate);
        jdbc.update("UPDATE character_backgrounds SET is_active = false WHERE character_id = ? AND code = 'old'", mate);

        assertThat(backgrounds.backgrounds(mate))
                .extracting(BackgroundInfo::code)
                .containsExactly("classroom", "cafe");
        BackgroundInfo cafe = backgrounds.backgrounds(mate).get(1);
        assertThat(cafe.imageUrl()).endsWith("/bg/cafe.png").startsWith("http");
        assertThat(cafe.guide()).isEqualTo("카페에 갔을 때");
        assertThat(backgrounds.backgrounds(mate).get(0).imageUrl()).isEqualTo("https://other.cdn/classroom.png");

        // 지금 배경: 저장된 code가 활성 목록에 있으면 그것, 없으면(비활성 · 모름 · null) 기본
        assertThat(backgrounds.current(mate, "cafe")).map(BackgroundInfo::code).contains("cafe");
        assertThat(backgrounds.current(mate, "old")).map(BackgroundInfo::code).contains("classroom");
        assertThat(backgrounds.current(mate, null)).map(BackgroundInfo::code).contains("classroom");
    }

    @Test
    void oneDefaultPerMate() {
        long mate = newMate();
        jdbc.update(
                "INSERT INTO character_backgrounds (character_id, code, name, image_url, is_default)"
                        + " VALUES (?, 'a', 'A', 'a.png', true)",
                mate);
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO character_backgrounds (character_id, code, name, image_url, is_default)"
                                + " VALUES (?, 'b', 'B', 'b.png', true)",
                        mate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** 모델 없는 준비 중 메이트 (schema CHECK: 준비 중이면 모델 없어도 됨) */
    private long newMate() {
        return jdbc.queryForObject(
                "INSERT INTO characters (name, coming_soon, is_active) VALUES ('배경 테스트', true, false) RETURNING id",
                Long.class);
    }
}
