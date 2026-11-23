-- 모듈: session (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 이 연결의 지금 배경 (schema.sql 2026-11-23, ADR 0032). NULL이면 메이트 기본 배경
-- character_backgrounds.code 값이지만 다른 모듈 테이블이라 FK는 두지 않는다 (배경을 지워도 연결 기록은 남김)
ALTER TABLE connections ADD COLUMN background_code text;
