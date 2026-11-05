# 캡슐 백엔드

AI 캐릭터와 함께 공부하는 웹서비스 캡슐의 백엔드.

## 처음이면
1. `scripts/setup.sh` (1인 1회)
2. `docs/HARNESS.md` 읽기: 작업 흐름, 구역 규칙, PR 규칙 전부 여기 있음. 코드 구조 · 파이프라인은 `docs/ARCHITECTURE.md`
3. `claude` 실행 → 세션 시작 시 현재 상태(나, 단계, 브랜치, 명세 스냅샷 시각)가 표시됨

## 자주 쓰는 명령
| 명령 | 용도 |
|---|---|
| `scripts/verify.sh` | 유일한 검증 기준 (PR 전) |
| Claude Code에서 "명세 바꿔줘" / "노션에 명세 반영해줘" | `docs/specs`(원본) 명세 PR / 머지된 명세를 노션 사본에 반영 (spec-sync) |
| `scripts/run-local.sh` | 로컬 실행 (worktree별 DB·포트 자동). 실제 LLM은 `.env.local`의 `CAPSULE_LLM_PROVIDER`(시연: openai) |
| `scripts/smoke-flow.sh [주소]` | 띄운 서버에 프론트 시나리오(가입 → 공부 → 채팅 → 자리 비움 → 쉬는 시간 → 종료 → 리포트)를 실제 요청으로. 단계별 대사 · 표정 · 호감도 표 |
| 캐릭터 대사 · 말투 고치기 | `src/main/resources/characters/<code>/profile.yml`을 고치고 맨 위 `version`을 올린다 (올려야 공용 DB · 개발 서버에 다시 들어감) |
| `scripts/new-migration.sh <모듈> <설명>` | 마이그레이션 생성 |
| `python3 .harness/zones.py explain <경로>` | 이 파일 누구 담당? 고쳐도 되나? |
