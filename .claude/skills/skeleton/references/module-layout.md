# 모듈 구조 규칙

모듈 경계는 `api`(ADR 0001), 모듈 바로 아래는 크루윙 백엔드와 같은 레이어 패키지(ADR 0012).

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
│   ├── controller/          AuthController, MeController          ┐
│   ├── service/             AuthService, TokenService, ...         │ 구현: 다른 모듈은 참조 금지
│   ├── dto/                 SignupRequest, MeResponse ... (파일 하나에 하나) │ → 모듈 구역
│   ├── repository/          UserRepository, TermsRepository ...    │
│   ├── domain/              User, UserMemory (JPA 엔티티)          │
│   ├── adapter/             UserQueryAdapter, MemoryQueryAdapter (계약 구현)
│   └── client/              KakaoClient, FakeKakaoClient, EmailSender, FakeEmailSender ┘
├── content/ ...  session/ ...  relation/ ...
└── dialogue/
    ├── client/LlmGateway.java      (LLM 입구. 공급자는 LlmProvider: Anthropic · OpenAi · Jev · Fake, capsule.llm.provider)
    ├── adapter/SessionClosingAdapter.java (session 계약 구현. dialogue는 api 패키지가 없음)
    └── ...
```

## 패키지
| 패키지 | 무엇을 두나 |
|---|---|
| `controller` | `@RestController` |
| `service` | `@Service`, 업무 규칙·계산 클래스(정책, 시간 계산), 스케줄러 |
| `dto` | 계층 사이를 오가는 record와 요청 값 enum. 예외 없이 여기, 파일 하나에 하나: 요청·응답, 서비스 결과, 외부 연동 입출력(`KakaoProfile`, `ProviderRequest`), 조회 조건(`MemoryStatus`) |
| `repository` | Spring Data 리포지토리, JdbcClient 리포지토리 |
| `domain` | JPA 엔티티, 엔티티에 저장되는 enum(`UserMemory.Type`), JPA 매핑용 record(`@Embeddable`) |
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
- 레이어 패키지는 모듈의 하위 패키지라 Spring Modulith가 다른 모듈에서 감춘다. 레이어끼리 쓰는 클래스는 public이어도 되고, 다른 모듈이 쓰면 `ModularityTests`가 실패한다.
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

## 코드 스타일 (ADR 0029)
흔한 Spring 레포 모양: `public` 기본 + Lombok. 모듈 경계는 접근 제어자가 아니라 Spring Modulith가 지킨다.

- main 코드의 클래스 · 메서드 · 생성자는 `public`, 필드는 `private`. 클래스 안에서만 쓰는 상수는 `private static final`
- 생성자 주입은 `@RequiredArgsConstructor` + `private final` 필드. 설정값은 필드에 `@Value`(lombok.config가 생성자로 옮김). 생성자 안에서 값을 가공 · 검증하는 클래스만 생성자를 직접 쓴다
- 엔티티는 `private` 필드 + `@Getter`(`getId()`, boolean은 `isX()`) + `@NoArgsConstructor(access = AccessLevel.PROTECTED)`. 값은 이름 있는 메서드로만 바꾼다. 내보내면 안 되는 필드(비밀번호 해시 등)는 `@Getter(AccessLevel.NONE)`
- `@Setter` · `@Data` · `@SneakyThrows` · `val`은 쓰지 않는다(lombok.config에서 컴파일 에러)
- DTO와 계약 타입은 `record`. 테스트 코드는 JUnit 5 관례대로 접근 제어자를 생략한다

```java
@Service
@RequiredArgsConstructor
public class ProfileService {
    private final UserRepository users;

    public MeResponse me(long userId) { ... }
}

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserMemory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    ...
}
```

## ModularityTests
```java
class ModularityTests {
    @Test
    void verifiesModularStructure() {
        ApplicationModules.of(CapsuleApplication.class).verify();
    }
}
```
