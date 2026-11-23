-- 모듈: content (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 새로 넣는 모델의 기본 형식을 Aliving2D 묶음으로 (2026-11-24, schema.sql · api.md 9).
-- 기본값만 바뀐다: 이미 있는 행(Cubism 메이트 · 아바타)은 지금 값(CUBISM) 그대로
ALTER TABLE live2d_models ALTER COLUMN format SET DEFAULT 'ALIVING2D_V2';
