-- 모듈: session (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 12/1 베타 3단계 (schema.sql 2026-11-23): 공부 세션 → 연결. 이름을 바꾸고 룸 칸을 지운다. 시간 칸 · 이벤트 종류는 다음 단계
-- 다른 모듈 테이블(chat_messages · llm_usages · conversation_recaps · relation_logs · relation_settlements)은
-- 칸 이름만 바꾼다 (FK가 이 테이블을 가리켜서 같은 파일에서 한 번에)

-- 1) 세션 캐릭터 → connections.character_id (세션마다 1행이라 그대로 옮김)
ALTER TABLE study_sessions ADD COLUMN character_id bigint REFERENCES characters (id);
UPDATE study_sessions s SET character_id = sc.character_id
FROM session_characters sc WHERE sc.session_id = s.id;
ALTER TABLE study_sessions ALTER COLUMN character_id SET NOT NULL;
DROP TABLE session_characters;

-- 2) 룸 · 배경 · 목표 시간 칸 삭제 (FK · UNIQUE(id, room_id) · idx_sessions_room_started도 같이 지워짐)
ALTER TABLE study_sessions
    DROP COLUMN room_id,
    DROP COLUMN background_id,
    DROP COLUMN target_minutes;

-- 3) 이름 바꾸기
ALTER TABLE study_sessions RENAME TO connections;
ALTER INDEX idx_sessions_user_started RENAME TO idx_connections_user_started;
ALTER INDEX uq_sessions_one_in_progress RENAME TO uq_connections_one_in_progress;
ALTER INDEX idx_sessions_in_progress RENAME TO idx_connections_in_progress;
CREATE INDEX idx_connections_character ON connections (character_id);

ALTER TABLE session_events RENAME TO connection_events;
ALTER TABLE connection_events RENAME COLUMN session_id TO connection_id;
ALTER INDEX idx_session_events_session RENAME TO idx_connection_events_connection;

-- 4) 다른 테이블의 session_id → connection_id
ALTER TABLE chat_messages RENAME COLUMN session_id TO connection_id;
ALTER INDEX idx_chat_messages_session RENAME TO idx_chat_messages_connection;
ALTER TABLE llm_usages RENAME COLUMN session_id TO connection_id;
ALTER TABLE conversation_recaps RENAME COLUMN session_id TO connection_id;
ALTER TABLE relation_logs RENAME COLUMN session_id TO connection_id;
ALTER TABLE relation_logs RENAME COLUMN session_event_id TO connection_event_id;
ALTER INDEX uq_relation_logs_session_event RENAME TO uq_relation_logs_connection_event;
ALTER TABLE relation_settlements RENAME COLUMN session_id TO connection_id;
