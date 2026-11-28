-- 모듈: session (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 연결 직후 취소 (schema.sql 2026-11-28, api.md 22 '취소'): end_type에 CANCELLED. 값을 늘리기만 한다
ALTER TABLE connections DROP CONSTRAINT connections_end_type_check;
ALTER TABLE connections ADD CONSTRAINT connections_end_type_check
    CHECK (end_type IN ('NORMAL', 'AUTO_CLOSED', 'AUTO_AWAY', 'MINOR_LIMIT', 'CANCELLED'));

