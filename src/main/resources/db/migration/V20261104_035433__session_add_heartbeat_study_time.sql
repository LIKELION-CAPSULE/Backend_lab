-- 모듈: session (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- api.md 21 (2026-11-04): heartbeat가 지금까지의 공부 시간을 보낸다. 자동 마감 때 마지막 값으로 세션 기록을 저장
ALTER TABLE study_sessions
    ADD COLUMN heartbeat_study_seconds   int,  -- 마지막 heartbeat의 studySeconds (쉬는 시간 제외)
    ADD COLUMN heartbeat_present_seconds int;  -- 그중 자리 지킴

