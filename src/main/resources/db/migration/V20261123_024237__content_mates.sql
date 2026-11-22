-- 모듈: content (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 12/1 베타 2단계 (schema.sql 2026-11-23): 메이트 화면 칸 · 모델 형식. 추가만 한다 (room_id · personality는 스터디룸 정리 때 지움)
ALTER TABLE characters
    ADD COLUMN one_liner        text,
    ADD COLUMN hashtags         text[]  NOT NULL DEFAULT '{}',
    ADD COLUMN age_rating       text    NOT NULL DEFAULT 'ALL' CHECK (age_rating IN ('ALL', 'ADULT')),
    ADD COLUMN world            text,
    ADD COLUMN my_role          text,
    ADD COLUMN illustration_url text,
    ADD COLUMN card_image_url   text,
    ADD COLUMN voice_sample_url text,
    ADD COLUMN stage_names      text[]  NOT NULL DEFAULT '{}',
    ADD COLUMN coming_soon      boolean NOT NULL DEFAULT false;

-- 지금 있는 캐릭터의 성격 태그를 해시태그로 옮겨 둔다 (화면이 바로 비지 않게)
UPDATE characters SET hashtags = personality;

ALTER TABLE live2d_models
    ADD COLUMN format text NOT NULL DEFAULT 'CUBISM' CHECK (format IN ('CUBISM', 'ALIVING2D_V2'));

ALTER TABLE live2d_motions ADD COLUMN label text;
ALTER TABLE live2d_expressions ADD COLUMN label text;
