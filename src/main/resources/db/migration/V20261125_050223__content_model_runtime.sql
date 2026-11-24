-- 모듈: content (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- Aliving2D 런타임 스크립트 경로 (schema.sql 2026-11-25, api.md 9 · 16 · 31 · 48). 버킷 루트 기준, CUBISM이면 NULL
ALTER TABLE live2d_models ADD COLUMN engine_path text;
ALTER TABLE live2d_models ADD COLUMN player_path text;
ALTER TABLE live2d_models ADD COLUMN reactions_path text;
