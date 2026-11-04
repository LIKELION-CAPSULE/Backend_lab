---
name: pr-write
description: 지금 세션의 작업 맥락으로 PR 본문을 쓰고 PR을 연다. 작업을 마치고 "PR 올려", "PR 써줘", "푸시하고 PR", "리뷰 요청하자"라고 할 때 반드시 사용. 리뷰어(상대와 상대의 Claude)는 이 세션 대화를 볼 수 없으므로, 맥락을 PR 본문으로 옮기는 것이 이 스킬의 목적이다.
---

# PR 쓰기

리뷰어는 diff만 본다. "왜 이렇게 했는지"는 지금 이 세션에만 있다. 그걸 PR 본문에 옮긴다.

## 절차
1. 검증. PR CI가 없으므로(ADR 0021 · 0023) 여기가 머지 전 마지막 검사다. 실패하면 PR을 만들지 않고 고친다.
   - 공용(`common`, gradle, yml) · 계약(`*/api/**`) · 마이그레이션 · 여러 모듈을 바꿨으면 `scripts/verify.sh` (full, E2E 포함)
   - 내 모듈 안만 바꿨으면 `scripts/verify.sh --quick` (Stop 훅이 이미 돌렸으면 그 결과로 갈음)
   - PR 직전에 `git rebase origin/develop` 후 검증한다. 합친 결과를 보는 CI가 없어서, rebase로 최신 develop 위에서 확인한다
   - `--quick`만 돌렸으면 마이그레이션 불변 검사를 따로: `python3 .harness/zones.py frozen-migrations` (full에는 들어 있음)
2. reviewer 서브에이전트로 셀프 리뷰. '반드시 고칠 것'이 있으면 고치고 다시 1.
3. 정보 수집
   - `git log --oneline origin/develop..HEAD`, `git diff --stat origin/develop...HEAD`
   - 구역: `git diff --name-only origin/develop...HEAD | python3 .harness/zones.py classify --author "$(gh api user -q .login)"`
4. 본문 작성: `.github/PULL_REQUEST_TEMPLATE.md` 양식 그대로(제목 줄을 바꾸지 않는다). 7에서 '작업 내용'을 검사한다(없거나 안내 문구뿐이면 실패)
   - **#️⃣연관된 이슈**: 이슈 번호, `J-xx` 백로그 ID, 관련 명세 번호. 없으면 '없음'. 이 PR로 이슈의 To-Do가 다 끝나면 `Closes #번호`(머지 때 자동으로 닫힘), 일부만 하면 `#번호`만 쓴다. 이슈 하나가 J 항목 여러 개를 묶고 있어서 일찍 닫히지 않게
   - **📝작업 내용**: 무엇을 왜. 리뷰어는 diff만 보므로 세션에만 있는 맥락을 아래 항목으로 짧게 (해당 없는 항목은 뺀다)
     - 결정과 이유: 코드만 봐서는 모를 선택. "무엇"이 아니라 "왜". 이 세션에서 사람과 합의한 내용 포함
     - 시도했다 버린 것: 되돌린 접근과 이유. 리뷰어의 "이렇게 하면 되잖아?"를 미리 막음
     - 일부러 안 한 것: 범위 밖, 남긴 TODO(judgment)
     - 검증: 돌린 verify(`--quick`/full)와 결과 한 줄, 직접 확인한 플로우
     - `docs/specs/api.md`를 바꿨으면 끝에 `### 코드 반영`: 영향받는 모듈마다 기능 PR · 이슈 번호, 브랜치, 또는 `없음 (이유)`. 비면 7의 검사가 실패한다 (spec-sync 스킬 A-5, ADR 0015)
   - **스크린샷**: 있으면. 없으면 비워 둔다
   - **💬리뷰 요구사항(선택)**: 3의 구역 판정에 cross-module/zone:contract/zone:shared/zone:migration이 나오면 그 파일과 이유를 반드시 적는다 (PR 라벨은 붙지 않으므로 본문이 유일한 표시)
   - 템플릿의 `>` 안내 문구는 지우고 내용으로 바꾼다
5. 문체: 짧은 한국어 문장. "~하는 것이 중요합니다", "다양한", "효율적인", 습관적인 세 개 나열, 문장마다 볼드를 쓰지 않는다. 근거 없는 "완벽하게/반드시 안전" 같은 단정도 쓰지 않는다.
6. 제목: 커밋 규칙과 같은 `<type>(<범위>): 한 줄 요약`. squash 머지하면 제목이 그대로 develop 커밋 메시지가 된다. 7에서 모양을 검사한다(ADR 0014)
   - type: `feat` 기능 · `fix` 버그 · `docs` 문서·명세 · `chore` 하네스·설정 · `test` 테스트 · `refactor` · `ci`
   - 범위: 주 모듈(`session`), 명세는 `specs`, 하네스는 `harness`. 소문자. 예) `feat(session): 공부 시작 API`, `docs(specs): 메시지 전송에 characterId 추가`
7. PR CI가 하던 검사를 로컬에서 (ADR 0023). 실패하면 고친 뒤 다시:
   ```bash
   printf '%s' "<제목>" | python3 .harness/zones.py check-pr-title
   flag=""; git diff --name-only origin/develop...HEAD | grep -qx 'docs/specs/api.md' && flag="--spec-changed"
   python3 .harness/zones.py check-pr-body $flag < <본문 파일>
   ```
   빌드 · 모듈 구성(`build.gradle` · `settings.gradle` · `zones.conf`)을 바꿨으면 `git diff --name-only origin/develop...HEAD | python3 .harness/zones.py adr-hint`가 비어 있는지도 본다(나오면 ADR을 같이 넣는다)
8. 본문 초안을 사람에게 보여주고 확인받은 뒤:
   `git push -u origin HEAD` → `gh pr create --base develop --title "<제목>" --body-file <파일>`
   (draft로 만들지 않고 리뷰어도 지정하지 않는다. 상대 리뷰는 필수가 아니다)
9. 사람에게 안내: PR CI는 없다. 1 · 7을 통과했으면 작성자가 머지한다(squash 권장). 위험 구역(계약 · 공용 · 마이그레이션 · 남의 모듈)을 건드렸으면 상대에게 한 줄 공유하고, 원하면 상대가 review-pr로 본다.

## 하네스 변경 PR
브랜치가 `harness/*`면 '리뷰 요구사항'에 "이 변경 후 두 사람의 에이전트 동작이 어떻게 달라지는지"를 적는다.
