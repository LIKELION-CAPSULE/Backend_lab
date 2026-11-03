# 명세

원본은 노션이다 (`SOURCE.md`). 기획을 고칠 때는 노션을 고치고, 레포에는 spec-sync 스킬로 가져온다.

| 파일 | 누가 만드나 | 용도 |
|---|---|---|
| `SOURCE.md` | 사람 (노션 페이지가 바뀌면 링크 수정) | 원본 위치, 마지막 동기화 시각 |
| `api.md` | spec-sync | API 명세 스냅샷 |
| `schema.sql` | spec-sync | DB 스키마 스냅샷 |
| `flow.md` | 사람 또는 skeleton 초안 | 핵심 흐름 → 전체 플로우 E2E 테스트 |
| `skeleton-plan.md` | skeleton | 설계 브리핑, 모듈 지도, 단순/판단 분류, API↔DB 불일치 |
| `judgment-backlog.md` | skeleton | 자리만 만든 판단 영역 → 도메인 단계에서 각자 가져감 |

왜 노션을 직접 읽지 않고 스냅샷을 두나: 에이전트, CI, 리뷰어가 같은 버전을 봐야 하고, 명세가 바뀐 내용이 PR diff로 보여야 해서 (`docs/decisions/0008`).
