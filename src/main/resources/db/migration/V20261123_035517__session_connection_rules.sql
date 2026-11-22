-- 모듈: session (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 12/1 베타 4단계 (schema.sql 2026-11-23): 연결 시간 칸 · 끝 종류 · 이벤트 4종
-- 제약 이름은 테이블 이름을 바꾸기 전(study_sessions · session_events) 이름 그대로 남아 있다

-- 1) 시간 칸: 자리 지킴 → 함께한 시간, 자리 비움 이름 정리, 쉬는 시간 삭제
ALTER TABLE connections RENAME COLUMN focus_seconds TO present_seconds;
ALTER TABLE connections RENAME COLUMN distract_seconds TO away_seconds;
ALTER TABLE connections RENAME COLUMN heartbeat_study_seconds TO heartbeat_connected_seconds;
ALTER TABLE connections DROP COLUMN break_seconds;

-- 옛 공부 시간(자리 지킴 + 자리 비움)을 연결 시간으로, 카메라 켠 시간은 함께한 시간으로 채운다 (CHECK를 지키게)
ALTER TABLE connections
    ADD COLUMN connected_seconds int NOT NULL DEFAULT 0,
    ADD COLUMN camera_on_seconds int NOT NULL DEFAULT 0,
    ADD COLUMN pip_seconds       int NOT NULL DEFAULT 0,
    ADD COLUMN message_count     int NOT NULL DEFAULT 0,
    ADD COLUMN mood              text,
    ADD COLUMN heartbeat_camera_on_seconds int,
    ADD COLUMN heartbeat_pip_seconds       int;
UPDATE connections SET connected_seconds = present_seconds + away_seconds, camera_on_seconds = present_seconds;
UPDATE connections SET heartbeat_camera_on_seconds = heartbeat_present_seconds, heartbeat_pip_seconds = 0
WHERE heartbeat_connected_seconds IS NOT NULL;

ALTER TABLE connections
    ADD CONSTRAINT connections_seconds_nonneg CHECK (
        connected_seconds >= 0 AND present_seconds >= 0 AND away_seconds >= 0
        AND camera_on_seconds >= 0 AND pip_seconds >= 0 AND message_count >= 0),
    ADD CONSTRAINT connections_seconds_order CHECK (
        present_seconds <= camera_on_seconds AND camera_on_seconds <= connected_seconds
        AND pip_seconds <= connected_seconds);

-- 2) 끝 종류: 자리 비움 자동 끊기 · 청소년 3시간
ALTER TABLE connections DROP CONSTRAINT study_sessions_end_type_check;
ALTER TABLE connections ADD CONSTRAINT connections_end_type_check
    CHECK (end_type IN ('NORMAL', 'AUTO_CLOSED', 'AUTO_AWAY', 'MINOR_LIMIT'));

-- 3) 이벤트: 베타 4종 추가. 옛 type 행은 기록이라 남긴다 (API는 받지 않음)
ALTER TABLE connection_events DROP CONSTRAINT session_events_type_check;
ALTER TABLE connection_events ADD CONSTRAINT connection_events_type_check
    CHECK (type IN ('PRESENT', 'AWAY', 'RETURN', 'IDLE_TALK', 'FOCUS', 'BREAK', 'DROWSY', 'YAWN', 'WAVE', 'HEAD_TURN'));
