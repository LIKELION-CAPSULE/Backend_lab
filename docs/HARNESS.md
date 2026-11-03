# 캡슐 하네스 설명서

> 하네스 = 에이전트(Claude Code)가 스스로 틀린 걸 알아채고, 두 사람의 에이전트가 같은 규칙으로 움직이게 만드는 장치.
> 이 문서가 원본. 하네스를 바꾸면 이 문서도 같은 PR에서 고친다.

---

## 먼저 읽기: 무엇을 골랐고 왜

하네스를 쓰기 전에 이 장만 읽으면 됨. 성욱에게 처음 설명할 때도 이 장 순서대로 하면 됨.

### 한 줄 답
- **하네스 프레임워크**: 기성 팩(Superpowers, pstack, CCPM)을 설치하지 않음. Claude Code 기본 기능(훅, 스킬, 서브에이전트, settings) + GitHub Actions(anthropics/claude-code-action)로 직접 구성함
- **앱 스택**: Spring Boot + Spring Modulith(모듈러 모놀리스) + PostgreSQL + Redis + Flyway + Testcontainers + Gradle(Spotless)
- **명세 원본**: 노션 (시연 API 최종본 DB, DB 페이지). 레포 `docs/specs/`에는 spec-sync 스킬이 가져온 스냅샷
- **개발 파이프라인**: 노션 명세 동기화 → 얇은 뼈대(단순한 건 구현, 판단·LLM은 자리만) → 도메인 분리 개발 → PR 3단 리뷰

### 하네스 프레임워크: 왜 기성 팩이 아니라 직접 구성했나

| 후보 | 잘하는 것 | 캡슐에 안 맞는 점 | 결론 |
|---|---|---|---|
| Superpowers | 계획 → TDD → 리뷰 공정이 잘 짜여 있음 | 모든 작업에 같은 공정을 돌림. TDD 강제 루프가 Testcontainers의 느린 테스트와 안 맞음. 다른 공정과 같이 쓰면 스킬끼리 충돌 | 아이디어만 (코드 전 계획 확인, 완료 전 검증) |
| pstack | 작업 크기별 라우팅, 다중 모델 검증 | Bun·Graphite 의존, 일부 TypeScript 전용, 토큰 많이 씀 | 아이디어만 (검증 방법을 스킬로 남기기) |
| CCPM | 팀용. GitHub 이슈로 진행 상황 공유 | PRD → 에픽 전체 공정이 MVP 일정에 무거움. 남의 파일 수정을 막지는 않음 | 판단 백로그 → 이슈 생성만 |
| multiclaude 등 오케스트레이터 | 병렬 자동화 | 에이전트 자율성이 높아 12월 MVP 일정에서 통제가 어려움 | 미채택 |
| **직접 구성 (선택)** | 캡슐 조건(2인, 모듈러 모놀리스, git flow)에 맞는 "구역" 규칙을 넣을 수 있음 | 유지보수를 우리가 함 | **채택** |

결정적 이유
- 조사한 기성 팩 어디에도 "두 사람이 모듈을 나눠 갖고, 남의 모듈을 건드리면 멈추는" 장치가 없었음. 이 부분은 어차피 직접 만들어야 했음
- 나머지(검증 루프, 리뷰, PR)는 Claude Code 기본 기능으로 충분했음. 추가 설치 없이 python3, git, gh만 필요
- 유지보수 부담을 줄이려고 판정 로직을 `.harness/zones.py` 한 파일에 모음

### 파이프라인 설계 이유

| 결정 | 이유 | 버린 대안 |
|---|---|---|
| 뼈대 먼저, 도메인 분리는 나중 | 공용 코드·계약·스키마를 둘이 갈라지기 전에 한 번에 깔아야 이후 충돌이 적음. 전체 플로우가 처음부터 돌아야 바로 테스트하며 볼 수 있음 | 처음부터 도메인 분리 (공용 코드와 계약을 둘이 동시에 만들다 충돌) |
| 판단·LLM은 Fake + 백로그 | 기획이 정해지기 전에 코드가 기획을 정해버리는 걸 막음. LLM 없이 E2E가 돌아서 비용 0 | 뼈대 단계에서 전부 구현 |
| 검증 명령 하나 (`verify.sh`) | 사람·에이전트·CI의 "통과" 기준이 같아짐 | 각자 gradle 명령 조합 |
| 규칙은 구조(훅·CI)에 | 프롬프트로만 지시한 규칙은 모델이 그때그때 판단해서 안 지킬 수 있음 | CLAUDE.md에만 적기 |
| 구역표 단일 원천 (`zones.conf`) | 로컬 훅과 CI 판정이 어긋나지 않음 | 훅과 CI에 규칙을 따로 작성 |
| 계약 변경은 확장 → 이전 → 제거 | 어느 단계도 상대 브랜치를 깨뜨리지 않음 | 시그니처 제자리 변경 |
| 타임스탬프 마이그레이션 + 모든 환경 out-of-order | 둘이 동시에 만들어도 번호가 안 겹침. 파일명으로 소유 모듈 판정. 만든 순서와 머지 순서가 달라도 배포 DB에 적용되게 out-of-order를 공용 설정에서 켬 | V1, V2 순번 / out-of-order는 로컬만 켜고 rebase 때 파일명 다시 짓기 |
| CI가 초록이면 작성자가 머지, 사람 리뷰는 선택 (2026-11-03 합의) | 2인 팀이라 상대 리뷰를 기다리면 둘 다 멈춤. 작업 맥락은 PR 본문에 남기고(pr-write), 위험한 PR은 Claude 자동 리뷰가 의견을 남김. 궁금하면 review-pr로 언제든 리뷰 | 상대 승인 필수 (처음 설계였으나 뺌) |
| Claude 자동 리뷰는 위험한 PR만 | 비용 | 모든 PR 자동 리뷰 |
| 충돌 예측 자동화는 안 함 | 구역 분리·짧은 브랜치·공용 PR 우선으로 대부분 예방되고, 남는 충돌은 rebase와 PR 화면에서 git이 알려줌. 자동 예측은 push마다 fetch 비용과 경고 소음만 늘림 | push·세션 시작 때 merge-tree로 상대 브랜치와 미리 합쳐보기 |
| git·GitHub가 하는 일은 다시 만들지 않음 | develop/main 보호는 GitHub 브랜치 보호, 로컬 파일 제외는 .gitignore, 리뷰 요청은 필요할 때 PR 화면에서, 뒤처짐은 git status가 이미 함. 같은 규칙을 두 군데서 관리하면 한쪽만 바뀌는 일이 생김. 예외: 브랜치 보호를 못 쓰는 동안 Claude의 develop/main push는 bash_guard가 막음(2-3장) | pre-commit·pre-push 훅, 셸 명령 차단으로 push 규칙 재구현 |
| 명세 원본은 노션, 레포엔 스냅샷 | 기획은 노션에서 쓰고 고치는 게 편함. 하지만 두 에이전트·CI·리뷰어가 같은 버전을 봐야 하고, 명세 변경이 PR diff로 보여야 함. CI는 노션을 못 읽음 | 에이전트가 매번 노션 직접 읽기, 레포에서 명세 작성 |

### 앱 스택

| 선택 | 이유 | 상세 |
|---|---|---|
| Spring Boot (Java 21) | 팀 주력 스택(Spring Boot/JPA) | - |
| Spring Modulith | 2인 팀에 MSA는 운영 부담. 모듈 경계를 테스트로 강제해서 둘이 모듈 단위로 나눠 일함 | `docs/decisions/0001` |
| PostgreSQL + Flyway | 관계형 중심. 스키마가 PostgreSQL 전용 기능(IDENTITY, text[], 부분 인덱스)을 씀. 스키마 변경 이력 공유 | `docs/decisions/0002` |
| Redis | 리프레시 토큰, 이메일 인증번호, 로그인 잠금 (DB 최종본 원칙: 만료 있는 값은 DB에 안 둠) | `docs/decisions/0009` |
| Testcontainers | H2의 "테스트 통과, 실서버 실패"를 막음 | `docs/decisions/0003` |
| git flow (가볍게) | main ← develop ← feature. 에이전트는 많이 바꾸므로 브랜치는 짧게 | `docs/decisions/0004` |
| Spotless | 포맷 논쟁 제거, Stop 훅이 자동 정리 | - |
| 하네스 직접 구성 | 위 표 | `docs/decisions/0006`, `0007`, `0010` |
| 명세 동기화 (노션 → 레포) | 위 표 | `docs/decisions/0008` |

### 이 원칙은 앞으로도 적용됨
- 프레임워크·라이브러리·구조·파이프라인을 새로 고르거나 바꿀 때는 **코드보다 이유 설명이 먼저**: 무엇을 / 왜 / 버린 대안 → 확인 → `docs/decisions/`에 ADR 추가
- skeleton 스킬은 계획 맨 앞에 "설계 브리핑"을 먼저 쓰고 확인받음 (3-2장)
- PR에서 `build.gradle`, `settings.gradle`, `zones.conf`를 바꿨는데 ADR이 없으면 CI가 `needs-adr` 라벨을 붙임

---

## 0. 원칙 3개, 그리고 그래서 생긴 것

| 원칙 | 그래서 이렇게 만듦 |
|---|---|
| 사람·Claude Code·CI가 **같은 검증 명령 하나**를 돌린다 | `scripts/verify.sh` 하나. Stop 훅도 CI도 이것만 부름 |
| 전체 플로우가 끝까지 도는 **얇은 뼈대 먼저**, 살은 나중 | skeleton 스킬 + `FlowE2ETest`. 판단이 필요한 곳은 자리만 만들고 `judgment-backlog.md`로 넘김 |
| 하네스도 코드처럼 **레포에 커밋하고 PR로 바꾼다** (git flow) | 하네스 파일 전부 레포 안. `harness/*` 브랜치에서만 바꾸고, 판정 규칙은 `.harness/zones.py` 한 곳 |

밑바탕 원칙 하나 더: **지켜야 하는 규칙은 프롬프트(CLAUDE.md)가 아니라 구조(훅·CI·테스트)에 둔다.** CLAUDE.md에 "~하지 마"라고 적은 규칙은 모델이 그때그때 판단해서 안 지킬 수 있음 (`docs/decisions/0005`).

---

## 1. 전체 흐름

```
[Phase A] 얇은 뼈대 (승희)                 [Phase B] 도메인 개발 (둘이 나눠서)            [Phase C] 예정
 노션 명세 동기화                            judgment-backlog에서 내 항목 가져감            LLM 대화 평가 하네스
  (spec-sync → api.md, schema.sql)            feature/<모듈>-<설명> 브랜치                   (골든 대화 회귀 테스트)
      ↓                                      내 모듈 안에서 개발                            → dialogue 담당(승희)이
 "뼈대 만들어" → skeleton 스킬                계약 변경은 확장→이전→제거                       실제 LLM 붙일 때 추가
      ↓                                      PR(pr-write) → 자동 라벨·Claude 의견 → CI 초록이면 머지
 계획 확인(skeleton-plan.md)                  상대 PR은 review-pr로 내 맥락 반영해 리뷰
      ↓                                      머지 → 다음 칸
 단순=구현 / 판단·LLM=Fake+백로그
      ↓
 verify 통과 → PR → 머지
      ↓
 .harness/phase: skeleton → domain (작은 harness PR)
```

`.harness/phase` 파일 한 줄이 단계를 정함. 단계에 따라 훅의 구역 판정이 달라짐(5장).

### 모듈과 담당 (제안안, 뼈대 설계 브리핑에서 확정)

| 모듈 | 담당 | 테이블 | API (노션 순서) |
|---|---|---|---|
| user | 성욱 | users, user_social_accounts, terms, term_versions, user_terms, user_memories | 1~8, 10, 13, 14, 24 |
| content | 성욱 | live2d_*, avatars, study_rooms, room_backgrounds, characters, character_contents | 9, 12 |
| session | 성욱 | study_sessions, session_characters, session_events | 11, 16~18, 21~23 |
| relation | 승희 | user_character_relations, relation_logs, user_unlocked_contents, user_unlocked_backgrounds | (18, 22에서 호출됨) |
| dialogue | 승희 | conversations, chat_messages, llm_usages | 19, 20 |

나눈 이유: DB 최종본의 섹션 구분을 그대로 따름. 성욱 쪽은 대부분 단순 구현(인증, 콘텐츠 조회, 세션 기록)이고, 승희 쪽은 기획 판단이 몰린 호감도·해금 공식과 LLM 대화. 판단이 몰린 두 모듈을 한 사람이 맡아야 서로 기다리지 않음.

---

## 2. 처음 한 번 하는 세팅

### 2-1. 승희: 저장소 만들기
저장소: https://github.com/LIKELION-CAPSULE/Backend_lab (2026-11-03, 아래 1~3 완료)

1. 이 하네스 파일들을 새 저장소 루트에 복사
2. `.harness/zones.conf`의 `[team]`에 둘의 GitHub 아이디·git 이메일 채우기 (`git config user.email`과 같아야 함), `[modules]` 담당 확인
3. 첫 커밋과 브랜치 생성 (브랜치 보호를 켜기 전에)
   ```bash
   git init && git add -A
   git add --chmod=+x scripts/*.sh .claude/hooks/* .harness/zones.py   # Windows는 실행 비트를 기록하지 않음 → 안 하면 CI·Mac에서 Permission denied
   git commit -m "chore: 하네스 초기 설정"
   git branch -M main && git remote add origin https://github.com/LIKELION-CAPSULE/Backend_lab.git
   git push -u origin main
   git switch -c develop && git push -u origin develop
   ```
4. `scripts/setup.sh --me seunghee`

### 2-2. 성욱: 클론 후
```bash
git clone https://github.com/LIKELION-CAPSULE/Backend_lab.git && cd Backend_lab
scripts/setup.sh --me seonguk
```
둘 다 Windows(Git Bash). `python3`가 Microsoft Store 바로가기면 setup.sh가 실제 파이썬으로 연결하는 `~/bin/python3`를 자동으로 만든다(관리자 권한 불필요, 11장).

setup.sh가 하는 일: 도구 확인(python3 연결, git, docker, java 21, gh) → 내가 누구인지 `.claude/settings.local.json`에 저장(커밋 안 됨) → `.env.local` 생성 → Notion MCP 연결 여부 확인

둘 다 Claude Code에 Notion MCP를 한 번 연결 (spec-sync용): `claude mcp add --transport http notion https://mcp.notion.com/mcp` → Claude Code에서 `/mcp`로 노션 로그인

### 2-3. GitHub 저장소 설정 (승희, 한 번)
- **기본 브랜치**: `develop` (설정 완료 2026-11-03)
- **브랜치 보호: 못 씀 → 약속으로 대신함** (확인 2026-11-03: 조직이 Free 플랜이고 저장소가 private이라 API가 403. Student Pack Pro는 개인 계정용이라 조직 저장소엔 안 됨)
  - 약속: 빨간 PR은 머지 안 함 / develop·main에 직접 push 안 함 / CI가 초록이면 작성자가 머지 (상대 리뷰는 선택, 2026-11-03 합의)
  - Claude 쪽은 훅이 막음: develop·main에서의 파일 수정(zone_guard), develop·main으로의 push(bash_guard, ADR 0010 예외)
  - 나중에 조직을 Team 플랜으로 올리거나 저장소를 public으로 바꾸면: `develop`·`main`에 PR 필수, 상태 체크 `verify`·`zones` 필수(승인 수는 0), 직접 push·force push 금지
- **Claude 자동 리뷰 켜기** (7장, 승희가 한 번)
  1. Claude GitHub App 설치: https://github.com/apps/claude → Install → `LIKELION-CAPSULE` → `Backend_lab`만 선택 (조직 Owner 권한 필요)
  2. 토큰 만들기: 터미널에서 `claude setup-token` (브라우저 로그인 후 토큰이 출력됨, 구독 Pro/Max 사용)
  3. 시크릿 등록: `gh secret set CLAUDE_CODE_OAUTH_TOKEN -R LIKELION-CAPSULE/Backend_lab` → 토큰 붙여넣기 (토큰을 채팅·파일에 남기지 않음)
  - API 키로 돌리려면 `ANTHROPIC_API_KEY` 시크릿 + `.github/workflows/claude-*.yml`에서 해당 줄 주석 해제
  - 시크릿이 없으면 claude-review(위험 라벨 PR)와 `@claude`만 실패하고 나머지는 정상 동작함

---

## 3. Phase A: 얇은 뼈대 (승희)

### 3-1. 노션 명세 가져오기 (spec-sync)
명세는 노션에서 쓰고 고친다. 레포에는 spec-sync 스킬이 그대로 옮긴 스냅샷이 들어감.

| 노션 원본 | 레포 파일 | 내용 |
|---|---|---|
| 시연 API 최종본 (11/7) 인라인 DB | `docs/specs/api.md` | 엔드포인트 목록 + 행마다 요청·응답·에러·와이어프레임 대조 |
| DB 페이지 (SQL 코드 블록) | `docs/specs/schema.sql` | 테이블 25개, PostgreSQL 15 이상 |
| (없음) | `docs/specs/flow.md` | 선택. 없으면 skeleton이 API 순서·와이어프레임 번호(H01→H20)로 초안을 만들고 확인받음 |

```bash
git switch develop && git pull
git switch -c feature/skeleton
claude
> 노션 명세 동기화해줘
```
- 하네스 zip에는 초기 스냅샷이 들어 있음: `schema.sql`은 전체, `api.md`는 목록만. 첫 spec-sync 때 각 API 행의 상세가 채워짐
- 노션 링크와 마지막 동기화 시각은 `docs/specs/SOURCE.md`
- 스냅샷은 직접 고치지 않음. API와 DB가 서로 안 맞는 곳은 고치지 않고 목록으로 올림 (다음 단계에서 사람이 결정)

### 3-2. 뼈대 생성
같은 브랜치, 같은 세션에서 이어서:
```
> docs/specs로 뼈대 만들어
```
skeleton 스킬이 하는 일:
1. **설계 브리핑 + 계획만 먼저** 씀 → `docs/specs/skeleton-plan.md`. 맨 앞은 '설계 브리핑'(선택한 스택·구조와 이유, 이 명세에서 모듈을 이렇게 나눈 이유, 판단으로 분류한 이유, 버린 대안), 그다음 모듈 지도(테이블·API → 모듈)·단순/판단 분류·API와 DB 불일치 목록·계약 목록·E2E 시나리오. **여기서 사람이 확인**해야 코드 생성 시작. 이 브리핑이 뼈대 PR에서 성욱이 가장 먼저 읽는 부분이 됨
2. Gradle 프로젝트 기반 (Spotless, Spring Modulith, Testcontainers, Flyway, 프로필)
3. 마이그레이션 (`scripts/new-migration.sh`로 모듈별 분리)
4. **계약 먼저**: 각 모듈 `api` 패키지(인터페이스, DTO, 이벤트)
5. 단순한 것 전부 구현 + 테스트
6. 판단·LLM은 Fake 구현 + `// TODO(judgment): J-xx` + `docs/specs/judgment-backlog.md`
7. `FlowE2ETest` + `http/flow.http` + `ModularityTests`
8. `scripts/verify.sh` 통과까지 반복

뼈대 단계(`phase=skeleton`)에서는 훅이 모듈·계약·공용 구역을 막지 않음. 혼자 전체를 까는 단계라서. 단, develop 직접 수정, 규칙 위반 마이그레이션, 하네스 파일 수정은 그대로 막거나 물어봄.

### 3-3. 마무리
1. `PR 올려` → pr-write 스킬 → PR
2. 성욱이 훑어보기 (선택. 특히 `skeleton-plan.md`의 모듈 지도와 계약, `judgment-backlog.md`의 담당 배분)
3. 머지 후 단계 전환 PR:
   ```bash
   git switch develop && git pull && git switch -c harness/phase-domain
   echo domain > .harness/phase   # Claude에게 시켜도 됨(하네스 파일이라 확인 질문 뜸)
   ```
   → PR → 머지. 이때부터 Phase B 규칙 적용

---

## 4. Phase B: 도메인 개발 (둘이 나눠서)

### 4-1. 하루 흐름
1. **아침**: `git fetch && git rebase origin/develop`
2. **브랜치**: `git switch -c feature/<모듈>-<설명>` (develop에서 고치려 하면 훅이 막음)
3. **작업**: `judgment-backlog.md`에서 내 항목(J-xx) 가져가서 구현. 내 모듈 안은 훅이 조용히 통과
4. **노션 명세가 바뀌었으면**: spec-sync → 작은 명세 PR을 먼저 머지 (기능 PR에 섞지 않음)
5. **내 모듈 밖이 필요해지면**: 훅이 멈추고 물어봄 → 6장 표대로 판단
6. **수시로**: Claude가 멈출 때마다 Stop 훅이 `verify.sh --quick` → 실패하면 Claude가 계속 고침
7. **작게 자주 커밋, 하루 1번 이상 push, 작업 시작하면 draft PR** (상대가 내 진행 상황을 GitHub에서 볼 수 있게)
8. **끝나면**: `PR 올려` → pr-write → PR → CI(verify·zones)가 초록이면 내가 머지(squash) → 상대에게 한 줄 공유
9. **상대 PR 리뷰 (선택)**: 내 작업과 겹치거나 궁금하면 `PR 12 리뷰해줘` → review-pr 스킬. 머지 조건은 아님
10. feature 브랜치는 1~2일 안에 머지

### 4-2. 세션을 열면 Claude가 먼저 아는 것 (SessionStart 훅)
```
[캡슐 하네스 상태]
- 나: seunghee | 내 모듈: relation, dialogue
- 단계: domain (도메인 분리 개발. 내 모듈 밖 수정은 사람 확인을 거침)
- 브랜치: feature/dialogue-reply
- 명세 스냅샷: 2026-11-03 (원본은 노션. 바뀌었으면 spec-sync)
- 미구현 판단 목록: docs/specs/judgment-backlog.md (내 담당 항목부터)
```

### 4-3. 승희의 LLM 파트 (dialogue 모듈)
- 뼈대에는 `dialogue/api`(대화 계약) + `dialogue/internal/llm/LlmClient`(포트) + `FakeLlmClient`만 있음
- 실제 LLM 어댑터는 `capsule.llm.mode=real`일 때 주입되는 새 구현으로 추가 → Fake는 테스트·로컬 기본값으로 계속 유지 (E2E가 API 비용 없이 돎)
- API 키는 `.env.local` (Claude는 읽지 못하게 막혀 있음)
- 대화 품질 평가(골든 대화 세트)는 Phase C에서 하네스에 추가 예정

---

## 5. 구역 규칙

모든 파일은 `.harness/zones.conf`에 따라 구역 하나에 속함. 확인: `python3 .harness/zones.py explain <경로>`

| 구역 | 예시 | skeleton 단계 | domain 단계 |
|---|---|---|---|
| local | `.env.local`, `*.local.*`, `settings.local.json` | 허용 (.gitignore로 커밋 안 됨) | 허용 |
| 내 모듈 | `<base>/dialogue/**` (내가 담당) | 허용 | 허용 |
| 상대 모듈 | `<base>/user/**` (성욱 담당) | 허용 | **확인 질문** |
| 계약 | `<base>/*/api/**` | 허용 | **확인 질문** (내 모듈이어도) |
| 공용 | `common/**`, `build.gradle`, `application*.yml`, `docs/specs/**` | 허용 | **확인 질문** |
| 마이그레이션 | `db/migration/**` | 규칙 맞으면 허용 | 내 모듈 허용 / 남의 모듈 확인 |
| 하네스 | `.claude/`, `.harness/`, `scripts/`, `.github/`, `CLAUDE.md` | `harness/*` 브랜치만 허용, 그 외 확인 | 같음 |

항상 적용 (단계 무관):
- develop/main 브랜치에서 파일 수정 → **차단** (Claude에게 "브랜치 만들어"라고 전달됨)
- develop에 머지된 마이그레이션 수정 → **차단**
- 이름 규칙 안 맞는 마이그레이션 생성 → **차단**

판정 종류
- **허용**: 아무 일 없음
- **확인 질문(ask)**: Claude Code가 멈추고 사람에게 승인 여부를 물음. 사유는 사람 화면에만 보임
- **차단(deny)**: 실행 안 됨. 사유가 Claude에게 전달돼 Claude가 다른 방법을 찾음

셸 명령 차단 (`bash_guard`): 마이그레이션 삭제·이동, `.env` 읽기, develop/main으로 push(대상이 develop/main이거나 지금 develop/main 브랜치일 때). push 차단은 원래 GitHub 브랜치 보호의 일이라 그걸 못 쓰는 동안만 둠(2-3장, ADR 0010 예외). 그 밖의 위험한 명령은 Claude Code 기본 권한 확인이 물어봄

---

## 6. 상황별 대처

| 상황 | 하네스가 하는 일 | 사람이 할 일 |
|---|---|---|
| 내 모듈 안 수정 | 통과, Stop 훅이 검증 | 없음 |
| 상대 모듈 내부를 고쳐야 함 (버그 발견 등) | 확인 질문 → 승인 시 PR에 `cross-module` 라벨 | 원칙은 상대에게 요청. 급하면 승인하고 고친 뒤 카톡 한 줄 |
| 계약(api) 변경 | 확인 질문, PR에 `zone:contract`, Claude 자동 리뷰 | contract-change 스킬: 확장→이전→제거, 각각 별도 PR |
| 공용 수정 (`common`, gradle, yml) | 확인 질문, `zone:shared`, Claude 자동 리뷰 | 기능 PR에 섞지 말고 작은 공용 PR로 먼저 머지, 상대는 rebase |
| DB 변경 | 기존 파일 수정 차단, 남의 테이블은 확인 | 노션 DB 페이지 수정 → spec-sync → `scripts/new-migration.sh`, 컬럼 변경도 확장→이전→제거 |
| 노션 API 명세가 바뀜 | spec-sync가 스냅샷 갱신, 바뀐 부분이 PR diff로 보임 | 작은 명세 PR을 먼저 머지, 영향받는 모듈 담당자가 구현 |
| 로컬 전용 설정·비밀값 | .gitignore로 커밋 제외, Claude의 `.env` 읽기 차단 | `.env.local`, `*.local.*`에 둠 |
| 아직 커밋 안 한 작업 | 상대에게 안 보임 | 작게 자주 커밋, 하루 1번 push, 작업 시작하면 draft PR |
| 내 병렬 세션끼리 (worktree) | worktree별 DB·포트 자동 분리 (`run-local.sh`) | 세션당 worktree 하나, 동시 3개 이하 |
| 브랜치를 오래 끔 | 없음 (뒤처짐·충돌은 git status, rebase, PR 화면이 알려줌) | feature 1~2일, 매일 rebase |
| 텍스트 충돌은 없는데 합치면 깨짐 | CI가 "develop에 합친 결과"로 verify (ModularityTests + E2E) | 빨간 PR은 머지 안 함 |
| 하네스 자체 변경 | `harness/*` 아니면 확인 질문, PR에 `zone:harness` | 둘 다 리뷰. 이 문서도 같이 수정 |

### 계약 변경 예시: `UserQuery`에 닉네임이 필요해짐
| PR | 내용 | 상대 브랜치 영향 |
|---|---|---|
| 1 확장 | `getProfile(id)` 추가, 기존 `getSummary`는 `@Deprecated(forRemoval=true)`로 유지 | 없음 |
| 2 이전 | 호출하는 쪽을 `getProfile`로 옮김 (상대 모듈 호출부는 상대가) | 없음 |
| 3 제거 | 사용처 0 확인 후 `getSummary` 삭제 | 없음 (이미 다 옮김) |

---

## 7. PR과 리뷰 (3단)

리뷰에 필요한 맥락은 세 가지인데, 그중 "작업 맥락"은 작성자의 Claude 세션 안에만 있음. 그래서 PR 본문으로 옮기는 게 핵심.

| 맥락 | 예 | 어디서 오나 |
|---|---|---|
| 프로젝트 맥락 | 모듈 구조, 결정 이유, 규칙 | 레포 (CLAUDE.md, docs/decisions, zones.conf) |
| 작업 맥락 | 왜 이렇게, 무엇을 버렸나, 일부러 안 한 것 | **PR 본문** ← pr-write 스킬이 세션에서 옮김 |
| 상대 맥락 | 리뷰하는 사람이 지금 만들고 있는 것 | 리뷰어 자신의 세션 ← review-pr 스킬 |

| 단계 | 누가/어디서 | 무엇을 |
|---|---|---|
| 1. 셀프 리뷰 + PR 작성 | 작성자 세션 | reviewer 서브에이전트 → pr-write가 본문 6개 섹션 작성 → PR (draft·리뷰어 지정 없음) |
| 2. 자동 검사 | CI | `verify` (합친 결과로 전체 검증, 마이그레이션 불변) / `zones` (구역 라벨, 본문 섹션 검사) / `claude-review` (위험한 PR만: 계약·공용·마이그레이션·남의 모듈) |
| 3. 사람 리뷰 (선택) | 리뷰어 세션 + GitHub | 원할 때 review-pr로 "내 작업 기준" 의견. 머지 조건 아님 |

- PR 본문 필수 섹션: 목적 / 결정과 이유 / 시도했다 버린 것 / 일부러 안 한 것 / 검증 / 리뷰 포인트 (비면 CI 실패, 해당 없으면 "없음")
- 머지 조건은 CI(verify·zones) 초록뿐. Claude 자동 리뷰는 의견만 남기고 머지를 막지 않음
- 그 외 PR에서 Claude 의견이 필요하면 PR 댓글에 `@claude`
- 비용: 자동 리뷰는 위험 라벨이 붙은 PR이 열릴 때(또는 draft에서 Ready가 될 때)만 돎. review-pr는 각자 플랜 안에서 돎
- 리뷰어는 자동으로 지정하지 않음. 상대 의견이 필요하면 PR 화면에서 직접 요청

---

## 8. 하네스 지도 (파일별 역할)

| 파일 | 역할 | 언제 동작 |
|---|---|---|
| `.harness/zones.conf` | 구역표. 팀, 모듈 담당, 구역 패턴 (단일 원천) | 모든 판정의 입력 |
| `.harness/zones.py` | 판정 로직 + CLI (explain, whoami, new-migration, classify...) | 훅·CI가 공통으로 호출 |
| `.harness/phase` | 현재 단계 (skeleton / domain) | 훅 판정 |
| `.claude/settings.json` | 권한(.env 읽기 금지, 자주 쓰는 명령 허용) + 훅 등록 | Claude Code 실행 시 |
| `.claude/hooks/session_start.py` | 나·내 모듈·단계·브랜치(develop/main이면 경고)·명세 스냅샷 시각을 Claude에게 알려줌 | 세션 시작 |
| `.claude/hooks/zone_guard.py` | 파일 수정 직전 구역 판정 | Edit/Write 직전 |
| `.claude/hooks/bash_guard.py` | 마이그레이션 삭제·`.env` 읽기·develop/main push 차단 | Bash 직전 |
| `.claude/hooks/stop_verify.sh` | `verify.sh --quick`, 실패하면 Claude가 계속 고치게 (연속 3회까지) | Claude가 멈추기 직전 |
| `.claude/agents/reviewer.md` | 읽기 전용 셀프 리뷰 | pr-write가 호출 |
| `.claude/skills/spec-sync/` | 노션 API 명세·DB → `docs/specs` 스냅샷 | Phase A 시작, 명세 변경 시 |
| `.claude/skills/skeleton/` | 명세 → 뼈대 | Phase A |
| `.claude/skills/verify/` | 검증 실행과 실패 해석 | 실패 시 |
| `.claude/skills/pr-write/` | 세션 맥락 → PR 본문 → PR | PR 올릴 때 |
| `.claude/skills/review-pr/` | 상대 PR을 내 맥락으로 리뷰 | 리뷰할 때 |
| `.claude/skills/contract-change/` | 확장→이전→제거 절차 | 계약 변경 시 |
| `scripts/verify.sh` | **유일한 검증 명령** | 사람·Stop 훅·CI |
| `scripts/new-migration.sh` | 타임스탬프 + 모듈 이름 마이그레이션 생성 | DB 변경 시 |
| `scripts/run-local.sh` | worktree별 DB·포트로 로컬 실행 (`--reset`, `--info`) | 로컬 확인 |
| `scripts/setup.sh` | 1인 1회 세팅 | 클론 직후 |
| `.github/workflows/verify.yml` | 합친 결과로 verify, 마이그레이션 불변, zones 엄격 검사 | PR, develop push |
| `.github/workflows/pr-zones.yml` | 구역 라벨, `needs-adr`, PR 본문 검사 | PR |
| `.github/workflows/claude-review.yml` | 위험한 PR 자동 리뷰 | PR Ready |
| `.github/workflows/claude.yml` | `@claude` 댓글 응답 | 댓글 |
| `.gitattributes` | 텍스트 파일 LF 고정 (Windows에서 받아도 `.sh`·`gradlew`가 CRLF로 깨지지 않게) | checkout 시 |
| `CLAUDE.md` | 에이전트 상시 컨텍스트 (짧게, 상세는 링크) | 매 요청 |
| `docs/decisions/` | 스택·설계 결정 이유 (ADR) | 에이전트가 엉뚱한 대안 제안 방지 |
| `docs/specs/` | 노션 명세 스냅샷(api.md, schema.sql, SOURCE.md), 뼈대 계획, 판단 백로그 | Phase A·B |

---

## 9. 하네스를 바꾸는 법

1. `git switch develop && git pull && git switch -c harness/<설명>`
2. 수정 (이 브랜치에선 하네스 파일 수정이 확인 없이 허용됨)
3. `scripts/verify.sh` → PR. 본문 '리뷰 포인트'에 "이 변경 후 두 사람의 에이전트 동작이 어떻게 달라지는지"
4. CI가 초록이면 머지하고 상대에게 공유 → 각자 `git pull`하면 다음 세션부터 적용

자주 바꿀 것
- 모듈 추가/담당 변경: `zones.conf [modules]`
- 구역 패턴 추가: `zones.conf [zones]` (위에서부터 먼저 맞는 줄 적용. 구체적인 걸 위로)
- 단계 전환: `.harness/phase`
- 노션 명세 페이지가 옮겨지면: `docs/specs/SOURCE.md`의 링크

---

## 10. 참고한 것

| 출처 | 가져온 것 | 안 가져온 것 (이유) |
|---|---|---|
| Superpowers (obra) | 코드 전 계획 확인(skeleton 1단계), 완료 전 검증 | TDD 강제 루프 (Testcontainers 테스트가 느려 작업마다 반복하면 무거움), 팩 통째 설치 (다른 공정과 충돌) |
| pstack (poteto) | 검증 방법을 스킬로 남기기(verify), 작업 크기에 따라 절차 다르게 | 다중 모델 검증, Bun·Graphite 의존 |
| DeepSeek Harness Super-agent 글 | 규칙은 구조에(훅·CI), 사람이 동시에 보는 세션 3~5개 | Router/Worker 시스템 (2인 규모엔 사람이 그 역할) |
| CCPM | 판단 백로그 → GitHub 이슈, 작업 맥락을 GitHub에 남기기 | PRD→에픽 전체 공정 |
| Clash / Grove | - | 충돌 예측 자동화 (구역 분리 + 짧은 브랜치 + 공용 PR 우선으로 충분) |
| anthropics/claude-code-action | PR 자동 리뷰, `@claude` | 모든 PR 자동 리뷰 (비용) |
| Sentry 글쓰기 가이드, Humanizer | PR 본문 문체 규칙(pr-write) | - |

---

## 11. 문제 해결

| 증상 | 원인 | 해결 |
|---|---|---|
| 세션 시작 때 하네스 상태가 안 뜨고, 훅이 아무것도 안 막음 (Windows) | `python3`가 Microsoft Store 바로가기라 실행하면 exit 49. 훅은 exit 2만 차단이라 경고만 남기고 통과 | `scripts/setup.sh`를 다시 실행 → 실제 파이썬으로 연결하는 `~/bin/python3`를 자동으로 만듦 (Git Bash와 Claude Code 훅의 PATH 맨 앞이 `~/bin`). 직접 하려면 그 파일에 `#!/usr/bin/env bash`와 `exec /c/Python313/python "$@"` 두 줄 |
| 훅이 계속 "누구인지 모름" | HARNESS_ME 미설정, git 이메일이 zones.conf와 다름 | `scripts/setup.sh --me <키>` |
| 모든 파일 수정이 차단됨 | develop/main 브랜치에 있음 | `git switch -c feature/<모듈>-<설명>` |
| 확인 질문이 자주 뜸 | domain 단계에서 공용·계약·남의 모듈 작업 중 / 하네스 파일을 `harness/*` 밖에서 수정 | 원래 확인 대상. 하네스는 `harness/<설명>` 브랜치에서 |
| Claude가 끝내지 않고 계속 고침 | Stop 훅이 verify 실패를 돌려보낸 것 (정상) | 연속 3번까지만 막고, 그래도 실패하면 멈추고 사람에게 넘김. 그때 오류를 보고 방향 지시 |
| `Could not find a valid Docker environment` | Docker 꺼짐 | Docker Desktop 실행 |
| 로컬에서 Flyway `Detected applied migration not resolved locally` | 다른 브랜치에서 적용한 마이그레이션이 로컬 DB에 남아 있음(브랜치 전환) | `scripts/run-local.sh --reset` |
| `Migration checksum mismatch` | 적용된 마이그레이션 파일이 바뀜 | 되돌리고 새 마이그레이션 |
| CI `zones.conf 엄격 검사` 실패 | `[team]`에 TODO 남음 | GitHub 아이디·이메일 채우는 harness PR |
| CI `PR 본문` 실패 | 6개 섹션 중 빈 곳 | pr-write로 다시 쓰거나 '없음' 기입 |
| `claude-review` 실패 | 시크릿 없음 | 2-3장. 안 쓸 거면 무시해도 다른 체크엔 영향 없음 |
| spec-sync가 노션을 못 읽음 | Claude Code에 Notion MCP 미연결·미로그인 | 2-2장 Notion MCP 연결 |

---

## 12. 알려진 한계

- 훅은 Claude의 Edit/Write 도구만 봄. 셸로 파일을 쓰면(`sed -i`, `cat >`) 구역 판정을 거치지 않음 → CLAUDE.md에서 금지, CI(라벨·리뷰)가 뒤에서 잡음
- 확인 질문(ask)의 사유는 사람 화면에만 보임. Claude는 승인/거절 결과만 앎
- develop/main 보호: 지금은 GitHub 브랜치 보호를 못 써서(Free 플랜 조직의 private 저장소) 사람은 약속으로만 막힘. Claude는 훅이 develop/main에서의 파일 수정과 develop/main으로의 push를 막음. `git -C <경로> push`처럼 형태가 다른 명령까지는 못 잡음 (2-3장)
- 확인한 것 (2026-11-03, Windows 11 + Git Bash): Claude Code 안 훅 호출(SessionStart 상태 출력, zone_guard가 develop 수정을 실제로 차단), 한글·Windows 경로 판정, GitHub Actions `verify` 첫 실행(setup-java cache 실패를 찾아 고침)
- 아직 확인 못 한 것: 실제 Gradle 빌드, spec-sync의 노션 읽기 → 뼈대 단계에서 확인
- 대화 품질(LLM) 평가는 아직 없음 → Phase C
