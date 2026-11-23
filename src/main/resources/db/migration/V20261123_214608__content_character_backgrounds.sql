-- 모듈: content (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 메이트 배경 (schema.sql 2026-11-23, ADR 0032): 대화 흐름에 따라 결정 모델이 이 목록에서 고른다
-- 실제 배경 목록 · 이미지 · guide 문구는 에셋이 오면 다음 마이그레이션으로 넣는다 (J-18)
CREATE TABLE character_backgrounds (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id  bigint  NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    code          text    NOT NULL,
    name          text    NOT NULL,
    image_url     text    NOT NULL,
    guide         text,
    is_default    boolean NOT NULL DEFAULT false,
    sort_order    int     NOT NULL DEFAULT 0,
    is_active     boolean NOT NULL DEFAULT true,
    UNIQUE (character_id, code)
);
CREATE UNIQUE INDEX uq_character_backgrounds_default
    ON character_backgrounds (character_id) WHERE is_default;
