# 캡슐 백엔드

AI 캐릭터와 함께 공부하는 웹서비스의 백엔드. Spring Boot + PostgreSQL + Flyway, 모듈러 모놀리스(Spring Modulith).
2인 개발(승희, 성욱). 각자 Claude Code로 작업하고 PR로 합친다. 하네스 설명 전체: `docs/HARNESS.md`

## 작업 원칙: 이유 먼저
프레임워크·라이브러리·구조·파이프라인을 새로 고르거나 바꿀 때는 코드보다 먼저 "무엇을 / 왜 / 버린 대안"을 설명하고 확인받는다. 확정되면 `docs/decisions/`에 ADR을 추가한다. 지금까지의 선택과 이유: `docs/HARNESS.md` '먼저 읽기'

## 지금 단계
`.harness/phase` 파일이 기준. 세션 시작 시 훅이 현재 상태를 알려준다.
- `skeleton`: 명세(`docs/specs/`)로 얇은 뼈대를 만드는 중 → **skeleton 스킬 절차를 따른다**
- `domain`: 모듈을 나눠 개발 중 → 내 모듈 안에서 작업. 모듈 담당은 `.harness/zones.conf`

## 명세
원본은 레포 `docs/specs/api.md`·`schema.sql`(ADR 0013). 명세를 바꿀 때는 이 파일만 고치는 작은 명세 PR을 먼저 올려 기능 PR보다 먼저 머지하고, 머지되면 spec-sync 스킬로 노션 사본에 반영한다. api.md를 바꾸면 코드 반영까지 챙긴다: `zones.py spec-impact`로 영향받는 API를 찾고, 명세 PR 위에 기능 PR을 이어서 올린다(남의 모듈도 직접 고치고 담당자에게 공유). 명세 PR 본문 `### 코드 반영`이 비면 pr-write의 본문 검사가 실패한다(ADR 0015). 노션은 프론트·기획이 보는 읽기 전용 사본이라 노션을 고치지 않는다(노션에서 고친 흔적이 보이면 사람에게 알린다). 기획 판단이 필요한 내용은 명세에 지어 넣지 않고 "확정 필요"로 표시한다.

## 명령
- 검증(유일한 기준): `scripts/verify.sh --quick` (작업 중, Stop 훅이 자동) / `scripts/verify.sh` (PR 전, 공용 · 계약 · 마이그레이션 · 여러 모듈을 바꿨을 때. 머지된 마이그레이션 불변 검사 포함). PR CI는 없다(ADR 0021 · 0023)
- 마이그레이션 생성: `scripts/new-migration.sh <모듈> <설명>` (파일을 직접 만들지 않는다)
- 로컬 실행: `scripts/run-local.sh` (Docker 로컬 DB) / `scripts/run-local.sh --shared` (공용 개발 DB, 마이그레이션은 develop 머지 때 Actions만, ADR 0017)
- 이 파일 누구 담당?: `python3 .harness/zones.py explain <경로>`

## 구조 규칙
- 모듈 = `<base>/<모듈>/api`(계약, 다른 모듈이 쓰는 유일한 입구) + 레이어 패키지(구현)
- 레이어는 모듈 바로 아래에 `controller`·`service`·`dto`·`repository`·`domain`(+`adapter`·`client`·`config`), 크루윙 백엔드와 같은 모양. 상세: `.claude/skills/skeleton/references/module-layout.md`, ADR 0012
- 다른 모듈은 `api`만 참조(레이어 패키지 참조 금지). 결과가 필요하면 상대 `api`, 알리기만 하면 이벤트. 다른 모듈 테이블 JOIN 금지
- 계약(`*/api/**`) 변경은 contract-change 스킬(확장→이전→제거). 기존 시그니처를 제자리에서 바꾸지 않는다
- develop에 머지된 마이그레이션은 수정 금지. 변경은 새 마이그레이션으로
- `TODO(judgment)` 자리(기획 판단·LLM)는 명세 근거 없이 구현하지 않는다. 목록: `docs/specs/judgment-backlog.md`
- 스택·설계 결정의 이유: `docs/decisions/`. 여기 적힌 결정과 다른 대안을 제안하려면 근거와 함께 먼저 묻는다

## git flow
- `main`(배포) ← `develop`(통합) ← `feature/<모듈>-<설명>`. 하네스 변경은 `harness/<설명>`
- develop/main에서 직접 수정·push 금지. feature 브랜치는 1~2일 안에 머지, 매일 `git rebase origin/develop`
- 기능 PR에 공용 변경(`common`, `build.gradle`, `application*.yml`)을 섞지 않는다 → 작은 공용 PR로 먼저
- PR은 pr-write 스킬로(본문은 `.github/PULL_REQUEST_TEMPLATE.md` 양식). PR CI는 없다(ADR 0023): pr-write가 PR 전에 검증과 제목 · 본문 검사(`zones.py check-pr-title` · `check-pr-body`)를 로컬에서 돌리고, 통과하면 작성자가 머지한다. 상대 리뷰는 필수가 아니다(원하면 review-pr 스킬)

## 하지 말 것
- `.env`·`.env.local` 등 비밀값 파일 읽기(변수 이름은 `.env.example`에서), 실제 LLM API 키 사용
- develop/main 직접 push. 조직 플랜상 GitHub 브랜치 보호가 없어 bash_guard가 대신 막음 → 항상 PR로. GitHub 웹에서 develop에 바로 커밋하는 것도 같다(로컬 훅이 못 막음). 웹에서 고칠 때도 브랜치를 만들고 PR로
- 셸로 파일 내용 쓰기(`sed -i`, `>`, `tee` 등). 구역 훅은 Edit/Write만 보므로 파일 수정은 항상 Edit/Write로 (`scripts/`의 명령은 예외)
- 훅이 막았을 때 우회. 막힌 이유를 해결하거나 사람에게 묻는다
