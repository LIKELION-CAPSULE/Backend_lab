-- 모듈: dialogue (담당: seunghee)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 12/1 베타 6단계 (schema.sql 2026-11-23): 대화방 원문을 연결이 끝나도 남긴다 (32 대화방 기록).
-- 카드에 넣었는지는 지우는 대신 recapped_at으로 표시. 지금 남아 있는 행은 앞선 종료에서 카드 쓰기에 실패한 것이라 null 그대로
ALTER TABLE chat_messages ADD COLUMN recapped_at timestamptz;
CREATE INDEX idx_chat_messages_room ON chat_messages (conversation_id, id);
