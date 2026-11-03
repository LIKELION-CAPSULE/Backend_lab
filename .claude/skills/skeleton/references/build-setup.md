# 프로젝트 기반 설정 (gradlew가 없을 때만)

버전은 작업 시점의 안정 버전을 확인해서 쓴다. 아래는 "무엇이 있어야 하는가"의 목록이다.

## 기본
- Java 21, Spring Boot (현재 안정 버전), Gradle Wrapper 포함 (`./gradlew`). 커밋할 때 `git add --chmod=+x gradlew` (Windows는 실행 비트를 기록하지 않아 CI에서 Permission denied)
- 루트 패키지: `.harness/zones.conf`의 `base_package` (기본 `com.capsule`)
- 애플리케이션 클래스: `<base>.CapsuleApplication`

## 의존성
- web, validation, data-jpa, actuator
- postgresql 드라이버, flyway-core, flyway-database-postgresql
- data-redis: 리프레시 토큰, 이메일 인증번호, 로그인 잠금 (DB 최종본 원칙: 토큰류는 DB에 안 둠)
- spring-modulith-starter-core (모듈 경계), 테스트: spring-modulith-starter-test
- 테스트: spring-boot-starter-test, spring-boot-testcontainers, testcontainers postgresql, junit-jupiter (Redis는 GenericContainer)
- (선택) springdoc-openapi: API 문서

## 빌드 설정에 꼭 넣을 것
```groovy
plugins {
    // ... spring boot, dependency-management
    id 'com.diffplug.spotless' version '<현재 안정 버전>'
}

spotless {
    java {
        target 'src/**/*.java'
        palantirJavaFormat()      // 또는 googleJavaFormat() — 둘이 하나로 합의
        removeUnusedImports()
    }
}

tasks.named('test') {
    useJUnitPlatform()
    filter { failOnNoMatchingTests = false }   // verify.sh --quick 이 모듈별 필터를 쓰기 때문
}
```

## 설정 파일
`src/main/resources/application.yml` (공용, 비밀값 없음)
```yaml
spring:
  application.name: capsule
  jpa:
    open-in-view: false
    hibernate.ddl-auto: validate      # 스키마는 Flyway만 바꾼다
  flyway:
    enabled: true
    out-of-order: true                 # 타임스탬프 버전이라 만든 순서 ≠ 머지 순서. 늦게 머지된 '더 이른' 마이그레이션도 배포 DB에 적용되게 (ADR 0002)
capsule:
  llm:
    mode: fake                         # fake | real (real은 dialogue 모듈 담당자가 도메인 단계에서 구현)
```
`application-local.yml` (커밋함. 비밀값은 환경변수로)
```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/capsule}
    username: capsule
    password: capsule
```
테스트는 Testcontainers를 쓴다:
```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");   // 스키마는 PostgreSQL 15 이상 전제
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    }
}
```
(H2 쓰지 않음: Flyway SQL과 PostgreSQL 전용 타입 때문에 "테스트는 통과, 실서버는 실패"가 생김)

## 루트에 이미 있는 것 (하네스가 제공)
`docker-compose.yml`(postgres, redis), `scripts/run-local.sh`(worktree별 DB·포트), `.env.example`
