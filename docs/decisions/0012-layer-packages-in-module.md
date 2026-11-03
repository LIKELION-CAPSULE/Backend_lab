# 0012 모듈 안 레이어 패키지

- 결정: 모듈 경계(`api`/`internal`, ADR 0001)는 그대로 두고, `internal` 안을 역할별 하위 패키지로 나눈다.
  - `controller` · `service` · `dto` · `repository` · `domain`(엔티티)
  - `adapter`: 계약 구현. 자기 모듈 `api`나 다른 모듈의 SPI(`ActiveSessionLookup`, `SessionClosingHandler`)를 구현하는 얇은 클래스, 이름은 `*Adapter`
  - `client`: 외부 연동 포트와 Fake(카카오·메일·LLM)
  - `config`: 설정 클래스
  - 클래스가 없는 패키지는 만들지 않는다
  - 계층 사이를 오가는 record와 요청 값 enum은 예외 없이 `dto`에 파일 하나씩 둔다: 요청·응답, 서비스 결과, 외부 연동 입출력(`KakaoProfile`, `LlmReply`), 조회 조건(`MemoryStatus`). 엔티티에 저장되는 enum(`UserMemory.Type`)과 JPA 매핑용 record(`StudySession.SessionCharacter`)는 `domain`에 둔다
- 이유: 파일이 모듈마다 10~20개라 한 패키지에 섞여 있으면 찾기 어렵다. 팀에 익숙한 controller/service/repository 배치를 쓰면 처음 보는 사람도 위치를 바로 안다.
- 대가: 하위 패키지끼리 쓰는 클래스는 public이 된다. 다른 모듈이 못 쓰게 막는 일은 Java 접근 제한이 아니라 Spring Modulith(`ModularityTests`)가 맡는다. 엔티티 필드를 다른 패키지에서 직접 바꾸던 곳은 엔티티 메서드로 옮겼다.
- 버린 대안: 최상위 레이어 구조(`com.capsule.controller.*`). 모듈 경계와 구역 판정(zones.conf)이 사라져 ADR 0001과 맞지 않는다. 지금처럼 `internal` 한 패키지에 두기도 버렸다(보기 어렵다는 팀 의견).
