---
name: review-pr
description: 상대가 올린 PR을 "내 작업 맥락"까지 반영해서 리뷰한다. "PR 12 리뷰해줘", "성욱 PR 봐줘", "/review-pr 12"처럼 PR 번호와 함께 리뷰를 요청할 때 반드시 사용. CI의 Claude 리뷰는 내 진행 중 작업을 모르므로, 그 빈칸을 이 스킬이 채운다.
---

# 상대 PR 리뷰 (내 맥락으로)

## 절차
1. PR 읽기: `gh pr view <N>`(본문 = 작성자의 맥락), `gh pr diff <N>`, PR 댓글에 Claude 자동 리뷰가 있으면 그것도.
2. 구역: `gh pr diff <N> --name-only | python3 .harness/zones.py classify --author <작성자 GitHub 아이디>`
3. **내 맥락 수집** (이 스킬의 핵심)
   - 지금 내 브랜치 작업: `git diff --stat origin/develop...HEAD`, 이 세션에서 하던 일
   - 내 열린 PR: `gh pr list --author @me`
   - 겹치는 파일: `gh pr diff <N> --name-only`와 `git diff --name-only origin/develop...HEAD`에 둘 다 나오는 파일
   - 코드를 직접 봐야 하면 `git fetch origin pull/<N>/head:review-<N>`
4. 코드를 직접 확인해야 하면 내 작업을 건드리지 않게 별도 worktree에서:
   `git worktree add ../capsule-review-<N> review-<N>` → 필요 시 그 안에서 `scripts/verify.sh --quick`
   끝나면 `git worktree remove ../capsule-review-<N>`
5. 판단 기준: PR 본문의 의도대로 됐는가 / 내 모듈·내 진행 중 작업을 깨뜨리는가 / 계약 변경이 확장→이전→제거를 따르는가 / 판단 영역을 임의로 정했는가

## 출력
```
## 리뷰 (내 작업 기준)
한 줄 판단: 머지 가능 / 고친 뒤 머지 / 논의 필요
반드시 고칠 것:
내 작업과의 충돌·영향: (내 브랜치/PR 기준 구체적으로)
질문:
```
6. 사람에게 보여주고, 원하면 `gh pr review <N> --comment --body-file <파일>`로 게시. 승인(approve)은 사람이 GitHub에서 직접 한다.
