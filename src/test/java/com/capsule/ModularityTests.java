package com.capsule;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** 모듈 경계 검사: 다른 모듈의 api 밖 패키지(controller·service·repository 등) 참조, 순환 의존이 있으면 실패한다. */
class ModularityTests {

    @Test
    void verifiesModularStructure() {
        ApplicationModules.of(CapsuleApplication.class).verify();
    }
}
