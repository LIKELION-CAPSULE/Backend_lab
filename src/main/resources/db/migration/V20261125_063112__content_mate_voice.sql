-- 모듈: content (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 메이트 목소리 (schema.sql 2026-11-25, ADR 0036). 대화 음성(api.md 28)과 '목소리 듣기' 샘플이 같은 값. NULL이면 운영 설정 기본값
ALTER TABLE characters ADD COLUMN tts_voice text;
ALTER TABLE characters ADD COLUMN tts_model text;
ALTER TABLE characters ADD COLUMN tts_instructions text;

