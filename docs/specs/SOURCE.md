# 명세 원본과 노션 사본

원본은 이 레포의 `docs/specs/`다 (2026-11-04부터, ADR 0013. 그 전에는 노션이 원본이었다).
명세를 바꿀 때는 `api.md`·`schema.sql`을 고치는 작은 명세 PR을 올리고 기능 PR보다 먼저 머지한다. 머지되면 spec-sync 스킬로 노션 사본에 반영한다.
노션은 프론트·기획이 보는 **읽기 전용 사본**이다. 노션에서 고치지 않는다.

| 레포 파일 (원본) | 노션 사본 | 반영 방법 |
|---|---|---|
| `api.md` | 시연 API 최종본 (11/7): https://app.notion.com/p/40cb9a07e5928153a1a9cc534c62d0b7 | `## 공통 규칙` → 상위 페이지의 '공통 규칙' 섹션. `## <순서>. ...` 섹션 → 인라인 DB `collection://7f4b9a07-e592-8337-842e-074721ec80cf`에서 같은 '순서'의 행(속성: 분류·설명·와이어프레임, 그리고 페이지 본문) |
| `schema.sql` | DB: https://app.notion.com/p/40db9a07e59280abad14da2dde00c864 | 맨 위 안내 주석을 뺀 나머지를 페이지의 SQL 코드 블록에 |
| `flow.md`, `skeleton-plan.md`, `judgment-backlog.md` | 없음 | 레포에만 |

노션 사본 반영: 2026-11-05 `b207fb6` (회원 탈퇴 27. DB 행 추가, HTTP method에 DELETE 선택지 추가. schema.sql은 안 바뀌어 DB 페이지는 `bea3ecd` 그대로). 그 전: `bea3ecd` (최종 정리: 아바타 9 · 16, 룸 배너 12, 목표 시간 최소 3분 16, 예시 JSON 18~20, 공부 포기 22, schema.sql 엔진 · 세계관 제거 · content_version · last_severe_at. 프론트 연동 가이드 페이지 새로), `f0d9a0b`. 노션 상위 페이지와 DB 페이지 맨 위에도 같은 커밋을 적는다.

## 이력 (노션이 원본이던 때, spec-sync로 가져옴)
- 7차(11-04): 동작 이벤트 3종 DROWSY · YAWN · WAVE. API 18·19번, schema.sql `session_events.type`·주석
- 6차(11-03): API 11번 todayStudySeconds
- 5차: 공통 규칙 404·405·형 변환, API 4·13·14·16·19·20·24번
- 4차: 명세 충돌 정리. 공통 규칙, API 6·7·12·14·16~20·22번, schema.sql 주석
- 3차: API 19·20번
- 2차: API 1·4·6·23번, schema.sql `study_sessions.closing_line`
