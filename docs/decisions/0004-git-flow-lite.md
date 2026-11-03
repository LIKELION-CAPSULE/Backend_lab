# 0004 git flow (가볍게)

- 결정: main ← develop ← feature/<모듈>-<설명>. 하네스는 harness/<설명>. release/hotfix 브랜치는 첫 배포 후 필요할 때.
- 이유: 에이전트는 하루에 사람보다 훨씬 많이 바꾸므로 브랜치를 오래 끌면 충돌이 커짐 → feature는 1~2일, 매일 develop rebase.
- 강제: CI + 약속. 원래는 GitHub 브랜치 보호인데, 조직이 Free 플랜이라 못 쓰는 동안(2026-11-03 확인)은 Claude의 develop/main push만 bash_guard가 막는다(0010 예외). git hook은 쓰지 않음.
