# 모듈 구조 규칙

```
<base>/
├── CapsuleApplication.java
├── common/                  # 공용 (예외, 응답 포맷, 보안 설정, BaseEntity) → 공용 구역
│   └── package-info.java    (@ApplicationModule(type = OPEN))
├── user/
│   ├── api/                 # 계약: 다른 모듈이 쓸 수 있는 유일한 입구 → 계약 구역
│   │   ├── package-info.java   (@NamedInterface("api"))
│   │   ├── UserQuery.java       (interface)
│   │   ├── UserSummary.java     (record DTO)
│   │   └── UserRegistered.java  (event record)
│   └── internal/            # 구현: 다른 모듈은 절대 참조 금지 → 모듈 구역
│       ├── User.java, UserRepository.java, UserService.java, UserController.java
├── content/ ...  session/ ...  relation/ ...
└── dialogue/
    ├── api/DialogueService.java
    └── internal/
        ├── llm/LlmClient.java          (포트)
        ├── llm/FakeLlmClient.java      (@ConditionalOnProperty capsule.llm.mode=fake, matchIfMissing=true)
        └── ...
```

## 규칙
- 다른 모듈은 `api` 패키지 타입만 쓴다. Spring Modulith는 하위 패키지를 기본 비공개로 보므로 `api`에 `@NamedInterface("api")`를 붙인다:
  ```java
  @org.springframework.modulith.NamedInterface("api")
  package com.capsule.user.api;
  ```
- `common`도 Spring Modulith에겐 모듈 하나라서 하위 패키지(`common/exception` 등)가 기본 비공개다. 모든 모듈이 쓰는 공용이므로 열어둔다(안 하면 ModularityTests가 `depends on non-exposed type`으로 실패):
  ```java
  @org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
  package com.capsule.common;
  ```
- 결과가 바로 필요하면 `api` 인터페이스 호출, 알리기만 하면 되면 이벤트(`ApplicationEventPublisher` + `@ApplicationModuleListener`).
- 각 모듈은 자기 테이블만 읽고 쓴다. 다른 모듈 테이블과 JOIN하지 않는다(필요하면 `api`로 조회).
- 엔티티를 `api`로 내보내지 않는다. DTO(record)로 변환해서 내보낸다.
- 테스트도 같은 모듈 패키지에 둔다(`src/test/java/<base>/<module>/...`) → 구역 판정과 `verify.sh --quick`의 모듈별 테스트 실행이 이 위치에 의존한다.

## ModularityTests
```java
class ModularityTests {
    @Test
    void verifiesModularStructure() {
        ApplicationModules.of(CapsuleApplication.class).verify();
    }
}
```
