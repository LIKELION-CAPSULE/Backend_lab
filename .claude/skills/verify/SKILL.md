---
name: verify
description: 캡슐 백엔드를 검증하고 실패를 해석한다. 구현을 마쳤을 때, "검증해", "테스트 돌려", "빌드 깨졌어", Stop 훅이 verify 실패를 알렸을 때, ModularityTests·Spotless·Testcontainers·Flyway 오류가 났을 때, 로컬에서 전체 플로우를 직접 확인하고 싶을 때 반드시 사용.
---

# 검증

검증 명령은 하나뿐이다: `scripts/verify.sh`. 다른 방법(개별 gradle 명령 조합)으로 "통과"를 판단하지 않는다. PR CI는 없다(ADR 0021 · 0023). 그래서 로컬 검증이 머지 전 유일한 테스트다. full은 머지된 마이그레이션 불변 · `zones.conf` 엄격 검사도 함께 한다.

| 언제 | 명령 | 하는 일 |
|---|---|---|
| 작업 중 수시로 (Stop 훅이 자동 실행) | `scripts/verify.sh --quick` | 포맷 자동정리 → 컴파일 → 모듈 경계 → 바뀐 모듈 테스트 |
| PR 올리기 전, 공용 · 계약 · 마이그레이션 · 여러 모듈을 바꿨을 때 (pr-write) | `scripts/verify.sh` | 포맷 검사 → 빌드 → 전체 테스트(E2E 포함) |

## 실패 해석
- **zones.conf 오류**: 하네스 설정 문제. 직접 고치지 말고 사람에게 알린다(하네스 변경은 harness/* PR).
- **spotlessCheck 실패**: `./gradlew spotlessApply` 후 다시.
- **컴파일 실패**: 다른 사람이 바꾼 계약 때문이면 `git log origin/develop -- '*/api/*'`로 확인. 계약이 바뀌었는데 내 코드가 옛 시그니처를 쓰면, deprecated 표시와 대체 메서드를 찾아 옮긴다.
- **ModularityTests 실패** (`Module 'x' depends on non-exposed type ... within module 'y'`):
  다른 모듈의 `api` 밖 패키지(controller·service·repository 등)를 참조한 것. 고치는 순서: ① 그 모듈 `api`에 이미 있는 걸로 대체 ② 없으면 이벤트로 바꿀 수 있는지 ③ 둘 다 안 되면 상대 모듈 `api`에 추가가 필요 → 계약 변경이므로 contract-change 스킬, 상대 모듈이면 담당자에게 요청.
- **Testcontainers 오류** (`Could not find a valid Docker environment`): Docker가 꺼져 있음. 사람에게 Docker 실행을 요청한다.
- **Flyway 오류**
  - `Validate failed: Migration checksum mismatch`: 적용된 마이그레이션 파일이 바뀜. 되돌리고 새 마이그레이션으로.
  - `Detected applied migration not resolved locally` (로컬 DB): 다른 브랜치에서 적용한 마이그레이션이 로컬 DB에 남은 것(브랜치 전환). `scripts/run-local.sh --reset`.
- **FlowE2ETest 실패**: 전체 플로우가 끊긴 것. 어느 단계에서 끊겼는지 보고, 그 단계 API가 계약대로 응답하는지 확인. E2E를 고쳐서 통과시키지 말 것(플로우가 실제로 바뀐 경우에만 명세와 함께 수정).

## 로컬에서 눈으로 확인
1. `scripts/run-local.sh` (worktree별 DB·포트 자동, 출력된 주소 확인)
2. `http/flow.http`를 IDE에서 위에서부터 실행
3. LLM은 기본 Fake(`capsule.llm.mode=fake`). 실제 LLM은 dialogue 담당자가 붙인 뒤 `real`로.
