-- 모듈: session (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 연결 중 단계 상승을 알린 마지막 단계 (schema.sql 2026-11-24, api.md 21 stageUp). NULL이면 시작 단계 (아직 안 알림)
ALTER TABLE connections ADD COLUMN notified_stage int;
