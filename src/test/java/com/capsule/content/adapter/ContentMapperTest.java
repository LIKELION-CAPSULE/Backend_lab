package com.capsule.content.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 이미지 칸 규칙 (schema.sql, #105): 상대 경로면 CDN 주소를 붙이고, 전체 URL이면 그대로 */
class ContentMapperTest {

    final ContentMapper mapper = new ContentMapper("https://cdn.example/bucket/");

    @Test
    void relativePathGetsCdnBase() {
        assertThat(mapper.asset("images/a.webp")).isEqualTo("https://cdn.example/bucket/images/a.webp");
        assertThat(mapper.asset("/images/a.webp")).isEqualTo("https://cdn.example/bucket/images/a.webp");
    }

    @Test
    void fullUrlStaysAsIs() {
        assertThat(mapper.asset("https://other.example/x.png")).isEqualTo("https://other.example/x.png");
        assertThat(mapper.asset("http://other.example/x.png")).isEqualTo("http://other.example/x.png");
    }

    @Test
    void emptyIsNull() {
        assertThat(mapper.asset(null)).isNull();
        assertThat(mapper.asset(" ")).isNull();
    }
}
