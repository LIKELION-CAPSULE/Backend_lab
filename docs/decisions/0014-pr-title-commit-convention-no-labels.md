# 0014 PR 제목은 커밋 규칙으로, PR 라벨은 붙이지 않음

- 결정
  - PR 제목은 커밋 메시지와 같은 `<type>(<범위>): 요약`. type은 `feat` · `fix` · `docs` · `chore` · `test` · `refactor` · `ci`, 범위는 소문자(모듈, `specs`, `harness` 등)이고 생략할 수 있다. CI(pr-zones)가 `zones.py check-pr-title`로 검사한다
  - pr-zones는 PR에 구역 라벨(`zone:*`, `module:*`, `cross-module`, `needs-adr`)을 붙이지 않는다. `needs-adr`는 CI 경고(`::warning::`)로 남긴다
- 이유
  - PR 제목은 develop 이력에 남는다: squash 머지(pr-write 권장)면 커밋 메시지가 되고, 머지 커밋이면 'Merge pull request' 다음 줄에 들어간다. 커밋은 이미 `feat(session): ...` 모양인데 PR 제목만 `[모듈] 요약`이라 이력이 두 모양으로 섞였다
  - 범위는 여러 모듈이면 쉼표(`refactor(dialogue,relation): ...`). GitHub Revert 버튼 제목(`Revert "..."`)은 통과시킨다
  - 라벨은 붙기만 하고 읽는 곳이 없었다. claude-review는 라벨을 읽지 않고 `zones.py classify`로 따로 판정한다. 사람은 라벨 뜻을 몰라 소음이 됐다(2026-11-04)
  - 위험 구역 표시는 남는다: pr-write가 본문 '리뷰 요구사항'에 적고, claude-review가 그 구역을 보고 의견을 남긴다
- 버린 대안
  - `[모듈] 요약` 유지: develop 이력이 커밋 규칙과 어긋남
  - 스킬 문구만 바꾸고 CI 검사는 안 함: GitHub 웹에서 연 PR이나 다른 세션에서 놓침 (0005: 규칙은 구조에)
  - 라벨 유지: 위 이유
- 대가: 제목이 틀리면 zones가 빨개진다. GitHub PR 화면에서 제목만 고치면 다시 검사한다. 이미 만들어진 GitHub 라벨과 지난 PR의 라벨은 지우지 않았다
