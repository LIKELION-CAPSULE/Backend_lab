# 모듈 구조 규칙

모듈 경계는 `api`/`internal`(ADR 0001), `internal` 안은 레이어 패키지(ADR 0012).

```
<base>/
├── CapsuleApplication.java
├── common/                  # 공용 (예외, 응답 포맷, 보안 설정) → 공용 구역
│   └── package-info.java    (@ApplicationModule(type = OPEN))
├── user/
│   ├── api/                 # 계약: 다른 모듈이 쓸 수 있는 유일한 입구 → 계약 구역
│   │   ├── package-info.java   (@NamedInterface("api"))
│   │   ├── UserQuery.java       (interface)
│   │   ├── UserTypes.java       (record DTO 모음)
│   │   └── ActiveSessionLookup.java (위 모듈이 구현하는 SPI)
│   └── internal/            # 구현: 다른 모듈은 절대 참조 금지 → 모듈 구역
│       ├── controller/      AuthController, MeController
│       ├── service/         AuthService, TokenService, ProfileService, MemoryService
│       ├── dto/             요청·응답 record (파일 하나에 하나: SignupRequest, MeResponse ...)
│       ├── repository/      UserRepository, UserMemoryRepository, TermsRepository
│       ├── domain/          User, UserMemory (JPA 엔티티)
│       ├── adapter/         UserQueryAdapter, MemoryQueryAdapter (계약 구현)
│       └── client/          KakaoClient, FakeKakaoClient, EmailSender, FakeEmailSender (외부 연동 포트 + Fake)
├── content/ ...  session/ ...  relation/ ...
└── dialogue/
    └── internal/
        ├── client/LlmClient.java       (포트. 포트에 딸린 record도 여기)
        ├── client/FakeLlmClient.java   (@ConditionalOnProperty capsule.llm.mode=fake)
        └── ...
```

## 패키지
| 패키지 | 무엇을 두나 |
|---|---|
| `controller` | `@RestController` |
| `service` | `@Service`, 업무 규칙·계산 클래스(정책, 시간 계산), 스케줄러 |
| `dto` | 요청·응답 record. 파일 하나에 하나 |
| `repository` | Spring Data 리포지토리, JdbcClient 리포지토리 |
| `domain` | JPA 엔티티와 엔티티 전용 enum |
| `adapter` | 계약 구현: 자기 모듈 `api`나 다른 모듈의 SPI를 구현하는 클래스. 이름은 `*Adapter`. 보통 서비스에 넘기는 얇은 클래스지만, 조회만 하는 모듈(content)은 구현 자체가 여기 있어도 된다 |
| `client` | 외부 연동 포트와 Fake |
| `config` | `@Configuration`, `@ConfigurationProperties` |

클래스가 없는 패키지는 만들지 않는다.

## 규칙
- 다른 모듈은 `api` 패키지 타입만 쓴다. Spring Modulith는 하위 패키지를 기본 비공개로 보므로 `api`에 `@NamedInterface("api")`를 붙인다:
  ```java
  @org.springframework.modulith.NamedInterface("api")
  package com.capsule.user.api;
  ```
- `internal` 하위 패키지끼리 쓰는 클래스는 public이어도 된다. 다른 모듈이 못 쓰게 막는 건 `ModularityTests`가 한다.
- `common`도 Spring Modulith에겐 모듈 하나라서 하위 패키지(`common/error` 등)가 기본 비공개다. 모든 모듈이 쓰는 공용이므로 열어둔다(안 하면 ModularityTests가 `depends on non-exposed type`으로 실패):
  ```java
  @org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
  package com.capsule.common;
  ```
- 결과가 바로 필요하면 `api` 인터페이스 호출, 알리기만 하면 되면 이벤트(`ApplicationEventPublisher` + `@ApplicationModuleListener`).
- 각 모듈은 자기 테이블만 읽고 쓴다. 다른 모듈 테이블과 JOIN하지 않는다(필요하면 `api`로 조회).
- 엔티티를 `api`로 내보내지 않는다. DTO(record)로 변환해서 내보낸다.
- 계약 구현은 계약마다 얇은 `adapter` 클래스로 둔다. 컨트롤러가 주입하는 서비스가 계약까지 구현하면, 다른 모듈 테스트의 `@MockitoBean`이 그 서비스까지 바꿔서 컨텍스트가 뜨지 않는다. 컨트롤러가 없는 모듈(예: relation)은 서비스가 계약을 구현해도 된다.
- 엔티티 필드는 다른 패키지에서 직접 바꾸지 않는다. 엔티티 메서드(`close(...)`, `heartbeat(now)`처럼 의도가 드러나는 이름)로 바꾼다. 컬럼 이름이 자바 필드 이름 규칙과 다르면(`offset_x` ↔ `offsetX`) `@Column(name = ...)`을 적는다.
- 점수처럼 여러 엔티티를 거치는 갱신에 `@Modifying(clearAutomatically = true)`를 쓰지 않는다. 컨텍스트 전체가 비워져 같은 트랜잭션의 다른 모듈 엔티티 변경이 사라진다. 필요하면 그 행만 `entityManager.refresh(entity)`.
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
