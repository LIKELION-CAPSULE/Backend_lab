-- 모듈: content (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 12/1 베타 3단계 (schema.sql 2026-11-23): 스터디룸 · 배경 삭제. 메이트 = 캐릭터 1명
-- session_connections(바로 앞 파일)가 세션의 룸 · 배경 FK를 먼저 지웠다
-- user_unlocked_backgrounds는 relation 모듈 테이블이지만 room_backgrounds를 가리켜서 여기서 같이 지운다 (쓰는 코드 없음)
DROP TABLE user_unlocked_backgrounds;
DROP TABLE room_backgrounds;

-- room_id를 지우면 FK · UNIQUE(id, room_id) · idx_characters_room도 같이 지워짐
ALTER TABLE characters DROP COLUMN room_id;
DROP TABLE study_rooms;

-- 성격 태그는 2단계에서 hashtags로 옮겼다
ALTER TABLE characters DROP COLUMN personality;

-- 준비 중 메이트는 모델이 없을 수 있다
ALTER TABLE characters ALTER COLUMN model_id DROP NOT NULL;
ALTER TABLE characters ADD CONSTRAINT characters_model_required CHECK (coming_soon OR model_id IS NOT NULL);
CREATE INDEX idx_characters_sort ON characters (sort_order);
