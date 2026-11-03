-- 모듈: session (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 출처: docs/specs/schema.sql 섹션 7 (2026-11-03 노션 스냅샷). relation_logs는 relation 모듈.

-- ---------------------------------------------------------------------
-- 7. 공부 세션 (S05, S06, S09, S10)
-- ---------------------------------------------------------------------
-- 공부 시간 = focus_seconds + distract_seconds (쉬는 시간 제외)
-- 세션은 사용자가 종료를 누를 때만 끝남. 탭이 닫히면 heartbeat 기준으로 자동 마감
CREATE TABLE study_sessions (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           bigint      NOT NULL REFERENCES users (id),
    room_id           bigint      NOT NULL REFERENCES study_rooms (id),
    background_id     bigint      NOT NULL,               -- 미선택이면 서버가 룸 기본 배경을 채움
    target_minutes    int         CHECK (target_minutes > 0),   -- 목표 시간 (건너뛸 수 있음, 달성해도 종료 안 함)
    started_at        timestamptz NOT NULL DEFAULT now(),
    ended_at          timestamptz,                        -- NULL이면 진행 중
    end_type          text        CHECK (end_type IN ('NORMAL', 'AUTO_CLOSED')),
    last_heartbeat_at timestamptz,                        -- 1분에 한 번 정도만 갱신
    focus_seconds     int         NOT NULL DEFAULT 0,     -- 아래 넷은 종료 시 session_events로 계산해 저장
    distract_seconds  int         NOT NULL DEFAULT 0,
    break_seconds     int         NOT NULL DEFAULT 0,
    return_count      int         NOT NULL DEFAULT 0,
    closing_line      text,                               -- 완료 대사 (종료 때 저장, 리포트 · 카드 공용)
    CHECK (ended_at IS NULL OR ended_at >= started_at),
    CHECK ((ended_at IS NULL) = (end_type IS NULL)),
    UNIQUE (id, room_id),                                 -- session_characters 복합 FK용
    FOREIGN KEY (background_id, room_id) REFERENCES room_backgrounds (id, room_id)  -- 그 룸의 배경만
);
CREATE INDEX idx_sessions_user_started ON study_sessions (user_id, started_at DESC);
CREATE INDEX idx_sessions_room_started ON study_sessions (room_id, started_at DESC);
CREATE UNIQUE INDEX uq_sessions_one_in_progress
    ON study_sessions (user_id) WHERE ended_at IS NULL;   -- 사용자당 진행 중 세션 하나
CREATE INDEX idx_sessions_in_progress
    ON study_sessions (last_heartbeat_at) WHERE ended_at IS NULL;  -- 자동 마감 배치용

-- 이번 세션에서 고른 캐릭터 (그 룸의 캐릭터 중 1명 이상, 공부 중 변경 불가)
CREATE TABLE session_characters (
    session_id   bigint NOT NULL,
    room_id      bigint NOT NULL,                     -- 세션의 룸과 같아야 함 (복합 FK로 강제)
    character_id bigint NOT NULL,
    PRIMARY KEY (session_id, character_id),
    FOREIGN KEY (session_id, room_id)   REFERENCES study_sessions (id, room_id) ON DELETE CASCADE,
    FOREIGN KEY (character_id, room_id) REFERENCES characters (id, room_id)
);
CREATE INDEX idx_session_characters_character ON session_characters (character_id);

-- 상태가 바뀔 때만 한 줄씩. 각 상태는 다음 이벤트가 올 때 끝남
--   FOCUS: 시작·쉬는 시간 종료 / AWAY: 자리 비움 / RETURN: 딴짓에서 복귀 / BREAK: 쉬는 시간
--   쉬는 시간 중에는 감지 이벤트를 남기지 않음 (서버가 무시)
CREATE TABLE session_events (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id     bigint      NOT NULL REFERENCES study_sessions (id) ON DELETE CASCADE,
    type           text        NOT NULL CHECK (type IN ('FOCUS', 'AWAY', 'RETURN', 'BREAK')),
    false_positive boolean     NOT NULL DEFAULT false,   -- '아니야' 버튼 (오탐이면 집중으로 계산)
    occurred_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_session_events_session ON session_events (session_id, occurred_at);
