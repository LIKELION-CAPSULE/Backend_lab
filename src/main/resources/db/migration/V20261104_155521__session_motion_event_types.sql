-- 모듈: session (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 동작 이벤트 4종 (api.md 18, 2026-11-04): 졸음 · 하품 · 손 흔들기 · 고개 돌림.
-- 감지된 순간 한 줄. 상태를 바꾸지 않고 시간 계산에 쓰지 않음 (서버가 처리). 값을 늘리기만 해서 기존 행에 영향 없음
ALTER TABLE session_events DROP CONSTRAINT session_events_type_check;
ALTER TABLE session_events ADD CONSTRAINT session_events_type_check
    CHECK (type IN ('FOCUS', 'AWAY', 'RETURN', 'BREAK', 'DROWSY', 'YAWN', 'WAVE', 'HEAD_TURN'));
