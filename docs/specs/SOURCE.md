# 명세 출처

원본은 노션. 레포의 `api.md`, `schema.sql`은 spec-sync 스킬이 만든 스냅샷이라 직접 고치지 않는다.

| 레포 파일 | 노션 원본 | 가져오는 방법 |
|---|---|---|
| `api.md` | 시연 API 최종본 (11/7): https://app.notion.com/p/40cb9a07e5928153a1a9cc534c62d0b7 | 이 페이지의 '공통 규칙' 섹션(목록 표 아래 `## 공통 규칙`으로) + 인라인 DB `collection://7f4b9a07-e592-8337-842e-074721ec80cf`의 행(순서 정렬, 빈 행 제외) + 각 행 페이지 본문 |
| `schema.sql` | DB: https://app.notion.com/p/40db9a07e59280abad14da2dde00c864 | 페이지의 SQL 코드 블록 그대로 |
| `flow.md` (선택) | 없음 | 사람이 쓰거나, skeleton이 API 순서·와이어프레임 번호로 초안을 만들고 확인받음 |

동기화 시각: 2026-11-04 (spec-sync 7차: 동작 이벤트 3종 DROWSY · YAWN · WAVE. API 18·19번, schema.sql `session_events.type`·주석. 6차(11-03): API 11번 todayStudySeconds. 5차: 공통 규칙 404·405·형 변환, API 4·13·14·16·19·20·24번. 4차: 명세 충돌 정리. 공통 규칙, API 6·7·12·14·16~20·22번, schema.sql 주석. 3차: API 19·20번. 2차: API 1·4·6·23번, schema.sql `study_sessions.closing_line`)
