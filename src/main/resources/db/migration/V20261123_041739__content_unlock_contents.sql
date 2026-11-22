-- 모듈: content (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 12/1 베타 5단계 (schema.sql 2026-11-23): 친밀도 해금 콘텐츠 = 표정 · 보이스 대사. 단계가 오르면 열림
-- 옛 LINE · MOTION · STORY · VOICE · 이벤트 기간은 쓰는 코드도 행도 없어서 한 번에 바꾼다 (혹시 남은 행은 지움,
-- user_unlocked_contents는 ON DELETE CASCADE로 같이 지워짐)
DELETE FROM character_contents;

-- 칸을 지우면 그 칸을 쓰는 CHECK도 같이 지워짐
ALTER TABLE character_contents
    DROP COLUMN motion_id,
    DROP COLUMN unlock_type,
    DROP COLUMN unlock_value,
    DROP COLUMN event_start_at,
    DROP COLUMN event_end_at;

ALTER TABLE character_contents DROP CONSTRAINT character_contents_type_check;
ALTER TABLE character_contents
    ADD CONSTRAINT character_contents_type_check CHECK (type IN ('EXPRESSION', 'VOICE_LINE')),
    ADD COLUMN expression_name text,
    ADD COLUMN unlock_stage int NOT NULL CHECK (unlock_stage BETWEEN 2 AND 5),
    ADD CONSTRAINT character_contents_expression_name CHECK ((type = 'EXPRESSION') = (expression_name IS NOT NULL));
CREATE INDEX idx_character_contents_unlock ON character_contents (character_id, unlock_stage);
