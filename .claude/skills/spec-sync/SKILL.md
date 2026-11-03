---
name: spec-sync
description: 노션에 있는 캡슐 API 명세(시연 API 최종본 DB)와 DB 스키마 페이지를 레포 docs/specs로 가져와 스냅샷을 갱신한다. "명세 동기화", "노션 명세 반영", "노션 API 바뀌었어", "DB 스키마 업데이트됐어", skeleton을 시작하기 전, api.md·schema.sql이 비어 있거나 오래됐을 때 반드시 사용.
---

# 노션 명세 → docs/specs 스냅샷

원본은 노션이다. 레포에는 같은 내용을 그대로 옮긴 스냅샷을 커밋한다. 에이전트·CI·리뷰어가 같은 버전을 보고, 명세 변경이 PR diff로 보이게 하려는 것이다.

## 전제
- Claude Code에 Notion MCP가 연결돼 있어야 한다. 노션 도구가 없으면 멈추고 안내한다:
  `claude mcp add --transport http notion https://mcp.notion.com/mcp` → Claude Code에서 `/mcp`로 로그인
- 원본 위치는 `docs/specs/SOURCE.md`에 있다. 거기 적힌 페이지만 읽는다.
- 노션은 **읽기만** 한다. 노션 페이지를 고치지 않는다.

## 절차
1. 브랜치 확인: Phase A면 `feature/skeleton`에서, 그 외엔 `feature/spec-sync-<yyyymmdd>`를 develop에서 새로 만든다.
2. **API**
   - SOURCE.md의 API 데이터 소스를 행 단위로 읽는다(빈 행 제외, `순서` 오름차순).
   - 각 행 페이지를 열어 본문(요청·응답·에러·비고·와이어프레임 대조)을 가져온다.
   - `docs/specs/api.md`를 다시 쓴다: 맨 위 주석(스냅샷 안내), 목록 표, 그리고 행마다
     `## <순서>. <메서드> <엔드포인트> — <이름>` + 분류·설명·와이어프레임·노션 URL + 페이지 본문.
   - 본문은 **요약하거나 고쳐 쓰지 않는다.** 노션 마크다운을 그대로 옮기고, 표·코드 블록 형식만 맞춘다.
   - 엔드포인트의 `\{` `\}` 같은 이스케이프는 `{` `}`로 되돌린다.
3. **DB**: SOURCE.md의 DB 페이지에서 SQL 코드 블록을 그대로 가져와 `docs/specs/schema.sql`을 다시 쓴다. 맨 위 4줄 주석(출처·직접 수정 금지)만 붙인다.
4. `SOURCE.md`의 동기화 시각을 갱신한다.
5. `git diff --stat docs/specs` 와 `git diff docs/specs`로 바뀐 점을 정리해 사람에게 보여준다:
   추가·삭제·변경된 엔드포인트, 추가·변경된 테이블/컬럼, 노션 행에 적혀 있는 "DB와 안 맞음" 메모.
6. 이미 뼈대가 있으면(`.harness/phase`가 domain) 영향만 정리하고 **코드는 고치지 않는다**:
   - 스키마 변경 → 필요한 새 마이그레이션 목록 (기존 마이그레이션은 수정 금지)
   - API 변경 → 영향받는 모듈과 담당자(`python3 .harness/zones.py explain <경로>`)
7. 이 변경만 담은 작은 PR을 pr-write로 올린다. 명세 PR은 기능 PR보다 먼저 머지한다.

## 하지 말 것
- 명세 사이의 불일치(API ↔ DB)를 스냅샷에서 고치기. 그대로 옮기고 PR '리뷰 포인트'에 목록으로 적는다.
- 노션에 없는 내용을 추측해서 채우기.
