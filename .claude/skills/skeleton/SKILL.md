---
name: skeleton
description: 노션에서 가져온 docs/specs의 API 명세·DB 스키마(와 기획 흐름)로 캡슐 백엔드의 얇은 뼈대(walking skeleton)를 만든다. 단순한 부분은 전부 구현하고, LLM 대화와 기획 판단이 필요한 부분은 Fake 구현 + TODO(judgment)로 남겨 판단 백로그를 만든다. "뼈대 만들어", "스켈레톤", "명세로 프로젝트 만들어", "specs 기반으로 구현 시작" 같은 요청이나 .harness/phase가 skeleton인 상태에서 구현을 시작할 때 반드시 사용.
---

# 얇은 뼈대 만들기

목표: 전체 플로우가 처음부터 끝까지 실제로 도는 가장 얇은 서버. 이후 둘이 도메인을 나눠 "칸을 채우는" 작업만 하면 되게 만든다.

## 왜 이렇게 하나
- 공용 코드, 모듈 계약(api), DB 스키마를 **둘이 갈라지기 전에** 한 번에 깔아둔다. 갈라진 뒤에 이걸 만들면 충돌의 대부분이 여기서 난다.
- 판단이 필요한 부분을 지금 구현하면 기획이 정해지기 전에 코드가 기획을 정해버린다. 그래서 자리만 만들고 목록으로 넘긴다.

## 입력 (원본은 노션, 레포엔 스냅샷)
- `docs/specs/api.md`: API 명세 (spec-sync가 노션에서 가져옴)
- `docs/specs/schema.sql`: DB 스키마 (spec-sync가 노션에서 가져옴)
- `docs/specs/flow.md`: 핵심 흐름 (선택)
api.md나 schema.sql이 없거나 상세가 "(spec-sync 실행 전)"이면 먼저 spec-sync를 돌린다.
flow.md가 없으면 api.md의 순서와 와이어프레임 번호(H01→H20)로 흐름 초안을 만들어 계획에 넣고 확인받는다.

## 절차

### 0. 전제 확인
- `.harness/phase`가 `skeleton`인지, 현재 브랜치가 `feature/skeleton`인지 확인. develop이면 `git switch -c feature/skeleton`.
- `python3 .harness/zones.py check` 통과 확인.

### 1. 설계 브리핑 + 계획 (코드 쓰기 전, 사람 확인 1회)
`docs/specs/skeleton-plan.md`를 작성한다. **맨 앞은 설계 브리핑**이다. 이 팀은 "무엇을 골랐고 왜"를 코드보다 먼저 확인한다. 이 섹션은 뼈대 PR에서 성욱이 가장 먼저 읽는다.

```markdown
## 0. 설계 브리핑
### 선택한 것과 이유
- 스택·구조: docs/HARNESS.md '먼저 읽기'와 docs/decisions를 요약하고 링크 (새로 정하지 않음)
- 이 명세에 대한 설계 판단: 모듈을 이렇게 나눈 이유 / 모듈 간 호출을 api로 둔 곳과 이벤트로 둔 곳, 그 이유 / 판단으로 분류한 항목과 이유
### 버린 대안
- 각 판단마다 한 줄
### 사람이 확인할 것
- 결정이 필요한 질문 (없으면 '없음')
```
docs/decisions에 없는 새 라이브러리·구조를 쓰려면 여기서 이유와 함께 제안하고, 확인되면 ADR을 추가한다.

그다음:
1. **모듈 지도**: 테이블 → 모듈, 엔드포인트 → 모듈. schema.sql의 섹션 주석(1. 회원, 2. 약관 ...)과 api.md의 분류를 근거로 쓴다. 모듈 목록은 `.harness/zones.conf [modules]`를 기준으로 하고, 맞지 않으면 "zones.conf 수정 제안"으로 따로 적는다(직접 고치지 않음).
2. **엔드포인트별 구현 수준**: `단순` 또는 `판단`, 이유 한 줄.
   - 명세에 `[판단]` 표시가 있으면 판단. `[단순]`이면 단순.
   - 단순: CRUD, 조회/목록/페이지네이션, 입력 검증, 명세에 규칙이 숫자·조건까지 적힌 상태 전이, 단순 집계, 인증 흐름
   - 판단: LLM 호출(대사·반응 생성, 요약, 메모리), 명세에 수치/공식/조건이 비어 있는 규칙(예: 호감도 증가량 미정), "추후", "TBD", "기획 논의 필요"가 붙은 것
   - 애매하면 판단으로 분류하고 이유를 적는다. 판단을 단순으로 잘못 분류하는 쪽이 더 비싸다.
3. **API ↔ DB 불일치 목록**: 응답에 필요한데 테이블에 없는 값, NOT NULL인데 요청에 없는 값 등. api.md에 노션 메모로 이미 적힌 것 포함. 고치지 말고 결정이 필요한 질문으로 '사람이 확인할 것'에 올린다.
4. **계약 목록**: 모듈 간 호출이 필요한 곳마다 `api` 인터페이스/DTO, 비동기로 충분한 곳은 이벤트. 모듈 간 직접 테이블 JOIN 금지.
5. **전체 플로우 E2E 시나리오**: flow.md를 테스트 단계로 옮긴 것.

브리핑과 계획을 보여주고 **사람이 확인할 때까지 코드를 쓰지 않는다.** 수정 요청이 오면 계획만 고친다.

### 2. 프로젝트 기반 (gradlew가 없을 때만)
`references/build-setup.md`대로 Gradle 프로젝트, 의존성, Spotless, Spring Modulith, Testcontainers, Flyway, 프로필(local/test)을 만든다. 버전은 그 시점의 안정 버전을 확인해서 쓴다.

### 3. 마이그레이션
- schema.sql을 모듈 지도대로 나눠 `scripts/new-migration.sh <모듈> <설명>`으로 파일을 만들고 내용을 채운다. 파일을 직접 만들지 않는다(이름 규칙 위반은 훅이 막음).
- FK 의존 순서대로 생성한다(스크립트가 버전을 1초씩 증가시켜 순서를 보장).

### 4. 계약 먼저
`references/module-layout.md`의 구조로 각 모듈의 `api` 패키지(인터페이스, record DTO, 이벤트)를 먼저 만든다. 구현보다 계약이 먼저 있어야 이후 둘이 서로를 기다리지 않는다.

### 5. 단순 부분 구현
- 각 모듈의 레이어 패키지(`domain`·`repository`·`service`·`controller`·`dto`, `references/module-layout.md`)에 구현하고 테스트를 붙인다.
- 다른 모듈은 반드시 `api`나 이벤트로만 부른다.

### 6. 판단 부분은 자리만
- 인터페이스(또는 포트) + 결정적인(항상 같은 값을 주는) Fake 구현을 만든다. 예: 호감도 증가량 → `return 1;`
- 코드에 `// TODO(judgment): J-xx <무엇이 미정인지>` 주석.
- `docs/specs/judgment-backlog.md`에 항목 추가 (형식은 `references/judgment-backlog-format.md`). 담당자는 zones.conf의 모듈 담당.

### 7. LLM 자리 (dialogue 모듈)
- `dialogue/api`에 대화 계약(예: `DialogueService.reply(...)`), `dialogue/client`에 `LlmClient` 포트와 `FakeLlmClient`.
- `capsule.llm.mode=fake`(기본, local/test)일 때 Fake가 주입되게 한다. 실제 LLM 어댑터는 만들지 않는다(승희가 도메인 단계에서 개발).
- Fake 응답은 고정 문자열 + 입력 일부 반영 정도로, E2E가 검증할 수 있게.

### 8. 전체 플로우 E2E
- `FlowE2ETest` 하나: Testcontainers PostgreSQL + 실제 HTTP 호출(MockMvc 또는 RestAssured)로 flow.md 순서대로.
- 같은 흐름을 `http/flow.http`로도 만든다(사람이 `scripts/run-local.sh`로 띄우고 눈으로 확인).
- `ModularityTests`: `ApplicationModules.of(<App>.class).verify()`.

### 9. 검증
`scripts/verify.sh --full`이 통과할 때까지 고친다. 실패 해석은 verify 스킬.

### 10. 마무리 보고
- 구현한 것 / 판단 백로그 개수와 담당자별 개수 / zones.conf 수정 제안 / 남은 위험을 짧게 보고.
- 백로그를 GitHub 이슈로 만들지 **물어본 뒤** 원하면 `gh issue create --label judgment --label owner:<키>`로 생성.
- 다음 단계 안내: pr-write 스킬로 PR → 둘이 리뷰·머지 → `harness/phase-domain` 브랜치에서 `.harness/phase`를 `domain`으로 바꾸는 작은 PR.

## 하지 말 것
- 판단 영역을 "그럴듯하게" 채우기. 기획 결정이 코드에 숨어버린다.
- 실제 LLM API 호출 코드 작성, API 키 읽기.
- 계획 확인 전에 코드 생성.
- 마이그레이션 파일 수동 생성, develop/main에서 작업.
- api.md·schema.sql 직접 수정 (원본은 노션. 틀려 보이면 불일치 목록에 올림).
