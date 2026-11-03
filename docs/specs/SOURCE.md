# 명세 출처

원본은 노션. 레포의 `api.md`, `schema.sql`은 spec-sync 스킬이 만든 스냅샷이라 직접 고치지 않는다.

| 레포 파일 | 노션 원본 | 가져오는 방법 |
|---|---|---|
| `api.md` | 시연 API 최종본 (11/7): https://app.notion.com/p/40cb9a07e5928153a1a9cc534c62d0b7 | 인라인 DB `collection://7f4b9a07-e592-8337-842e-074721ec80cf`의 행(순서 정렬, 빈 행 제외) + 각 행 페이지 본문 |
| `schema.sql` | DB: https://app.notion.com/p/40db9a07e59280abad14da2dde00c864 | 페이지의 SQL 코드 블록 그대로 |
| `flow.md` (선택) | 없음 | 사람이 쓰거나, skeleton이 API 순서·와이어프레임 번호로 초안을 만들고 확인받음 |

동기화 시각: 2026-11-03 (초기 스냅샷. api.md는 목록만, 상세는 spec-sync 첫 실행 때 채움)
