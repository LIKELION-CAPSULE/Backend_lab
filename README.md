# 캡슐 백엔드

AI 캐릭터와 함께 공부하는 웹서비스 캡슐의 백엔드.

## 처음이면
1. `scripts/setup.sh` (1인 1회)
2. `docs/HARNESS.md` 읽기: 작업 흐름, 구역 규칙, PR 규칙 전부 여기 있음
3. `claude` 실행 → 세션 시작 시 현재 상태(나, 단계, 브랜치, 명세 스냅샷 시각)가 표시됨

## 자주 쓰는 명령
| 명령 | 용도 |
|---|---|
| `scripts/verify.sh` | 유일한 검증 기준 (PR 전) |
| Claude Code에서 "노션 명세 동기화해줘" | 노션 API·DB → `docs/specs` 스냅샷 (spec-sync) |
| `scripts/run-local.sh` | 로컬 실행 (worktree별 DB·포트 자동) |
| `scripts/new-migration.sh <모듈> <설명>` | 마이그레이션 생성 |
| `python3 .harness/zones.py explain <경로>` | 이 파일 누구 담당? 고쳐도 되나? |
