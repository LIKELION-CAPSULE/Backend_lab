# 0012 모듈 안 레이어 패키지 (크루윙 백엔드 구조)

- 결정: 모듈 바로 아래에 레이어 패키지를 둔다. 크루윙 백엔드(`<도메인>/controller · service · dto · repository · domain`)와 같은 모양에, 모듈 사이 계약용 `api`만 더한다.
  ```
  com.capsule.<모듈>
  ├── api/         다른 모듈이 쓰는 유일한 입구 (@NamedInterface("api"))
  ├── controller/  service/  dto/  repository/  domain/
  ├── adapter/     계약 구현: 자기 모듈 api나 다른 모듈의 SPI(ActiveSessionLookup, SessionClosingHandler)를 구현. 이름은 *Adapter
  ├── client/      외부 연동 포트와 Fake (카카오·메일·LLM)
  ├── config/      설정 클래스
  └── engine/      dialogue만: 대화 엔진 (2026-11-04 추가, #11)
  ```
  - 클래스가 없는 패키지는 만들지 않는다
  - `dialogue/engine`: Spring·DB 없이 도는 순수 Java 대화 엔진(사용자 분석, 감독·배우 프롬프트, 감점, 상태 갱신). 레이어로 쪼개지 않고 한 패키지로 둔다. 브라우저 콘솔 v5에서 옮긴 코드라 문구·규칙을 `EngineParityTest`가 글자 단위로 고정하고, 레이어로 나누면 원본과 대조하기 어렵다. LLM은 `Llm.Client` 포트로만 부르고 구현은 `client`에 둔다
  - 계층 사이를 오가는 record와 요청 값 enum은 예외 없이 `dto`에 파일 하나씩 둔다: 요청·응답, 서비스 결과, 외부 연동 입출력(`KakaoProfile`, `LlmReply`), 조회 조건(`MemoryStatus`). 엔티티에 저장되는 enum(`UserMemory.Type`)과 JPA 매핑용 record(`StudySession.SessionCharacter`)는 `domain`에 둔다
- 이유: 팀이 크루윙에서 쓰던 구조라 파일 위치를 바로 안다. 처음에는 `api`/`internal` 아래에 레이어를 뒀는데 한 단계가 더 깊어 보기 어렵다는 의견으로 `internal`을 없앴다.
- 모듈 경계: Spring Modulith는 모듈의 하위 패키지를 기본으로 감춘다. `api`만 `@NamedInterface`로 열려 있으므로 다른 모듈이 `controller`·`service` 등을 쓰면 `ModularityTests`가 실패한다(ADR 0001 그대로). 하위 패키지끼리 쓰는 클래스는 public이 된다. 엔티티 필드를 다른 패키지에서 직접 바꾸던 곳은 엔티티 메서드로 옮겼다.
- 버린 대안
  - 최상위 레이어 구조(`com.capsule.controller.*`): 모듈 경계와 구역 판정(zones.conf)이 사라져 ADR 0001과 맞지 않는다.
  - `api`/`internal/<레이어>`: 경계는 같은데 한 단계 더 깊다(위 이유).
  - 레이어 없이 모듈 한 패키지: 파일이 모듈마다 10~40개라 찾기 어렵다.
