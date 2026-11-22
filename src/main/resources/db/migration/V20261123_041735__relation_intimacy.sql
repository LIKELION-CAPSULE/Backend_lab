-- 모듈: relation (담당: seunghee)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 12/1 베타 5단계 (schema.sql 2026-11-23): 친밀도. 호감도 칸 · 기록은 대화가 아직 써서 그대로 둔다 (확장만)
ALTER TABLE user_character_relations
    ADD COLUMN stage                   int NOT NULL DEFAULT 1 CHECK (stage BETWEEN 1 AND 5),
    ADD COLUMN credited_seconds        int NOT NULL DEFAULT 0 CHECK (credited_seconds >= 0),
    ADD COLUMN total_connected_seconds int NOT NULL DEFAULT 0 CHECK (total_connected_seconds >= 0);

-- 하루(오전 5시 기준) 인정된 시간. 하루 한도 2시간
CREATE TABLE relation_daily (
    relation_id      bigint NOT NULL REFERENCES user_character_relations (id),
    day              date   NOT NULL,
    credited_seconds int    NOT NULL DEFAULT 0 CHECK (credited_seconds BETWEEN 0 AND 7200),
    chat_turns       int    NOT NULL DEFAULT 0 CHECK (chat_turns >= 0),  -- 그날 대화 턴 수 (0이면 인정 절반)
    PRIMARY KEY (relation_id, day)
);

ALTER TABLE relation_settlements
    ADD COLUMN credited_seconds int NOT NULL DEFAULT 0,
    ADD COLUMN stage_before     int NOT NULL DEFAULT 1,
    ADD COLUMN stage_after      int NOT NULL DEFAULT 1;
