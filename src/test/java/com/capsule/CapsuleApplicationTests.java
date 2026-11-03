package com.capsule;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** 전체 컨텍스트가 실제 PostgreSQL·Redis로 뜨는지 (마이그레이션·시드 적용, ddl-auto validate 포함) */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CapsuleApplicationTests {

    @Test
    void contextLoads() {}
}
