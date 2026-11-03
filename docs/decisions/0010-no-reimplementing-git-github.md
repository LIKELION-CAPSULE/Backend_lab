# 0010 git·GitHub가 하는 일은 하네스가 다시 만들지 않는다

- 결정: develop/main 보호는 GitHub 브랜치 보호, 로컬 파일 제외는 .gitignore, 리뷰 요청이 필요하면 GitHub PR 화면에서(2026-11-03부터 사람 리뷰는 필수가 아니라 자동 지정 안 함), 뒤처짐·충돌 확인은 git status·rebase·PR 화면에 맡긴다. git hook(pre-commit, pre-push)과 셸 명령의 push 규칙 차단은 쓰지 않는다.
- 이유: 같은 규칙을 두 군데서 관리하면 한쪽만 바뀌는 일이 생긴다. 하네스는 git·GitHub가 모르는 것(모듈 담당 구역, 마이그레이션 불변, 검증 기준, 명세 동기화)만 맡는다.
- 대가: 브랜치 보호를 못 쓰는 플랜(무료 비공개 저장소)이면 develop 직접 push는 약속으로만 막힌다.
- 예외 (2026-11-03): 조직이 Free 플랜이고 저장소가 private이라 브랜치 보호를 못 쓴다. 그동안 Claude의 develop/main push는 bash_guard가 막는다. 막아줄 곳이 없어서 두 군데 관리 문제가 생기지 않기 때문이다. 사람의 push는 약속으로만 막힌다. 브랜치 보호를 켜면 이 규칙을 지운다.
