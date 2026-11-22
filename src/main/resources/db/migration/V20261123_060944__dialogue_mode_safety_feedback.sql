-- 모듈: dialogue (담당: seunghee)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 12/1 베타 남은 것 (schema.sql 2026-11-23): 대화 모드 · 안전 · 답변 평가(40) · 신고(41)
ALTER TABLE chat_messages
    ADD COLUMN input_type text CHECK (input_type IN ('TEXT', 'QUICK', 'VOICE')),  -- USER만 (2026-11-23 전 행은 null)
    ADD COLUMN mode       text CHECK (mode IN ('BASIC', 'ADVANCED')),             -- CHARACTER만: 이 답장의 대화 모드
    ADD COLUMN rating     text CHECK (rating IN ('UP', 'DOWN')),                  -- CHARACTER만: 사용자 평가 (api.md 40)
    ADD COLUMN hidden_at  timestamptz;                                            -- 신고로 가린 시각 (api.md 41)

-- 답변 신고 (api.md 41). 신고한 사람 · 답장마다 한 번. 처리는 베타 동안 운영자가 DB에서
CREATE TABLE chat_reports (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     bigint      NOT NULL REFERENCES users (id),
    message_id  bigint      NOT NULL REFERENCES chat_messages (id) ON DELETE CASCADE,
    reason      text        NOT NULL CHECK (reason IN ('INAPPROPRIATE', 'VIOLENT', 'OUT_OF_CHARACTER', 'OTHER')),
    detail      text,
    status      text        NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED')),
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, message_id)
);

-- 안전 위반 기록 (api.md 20 '안전'). 24시간 안 3번이면 24시간 대화 정지 (횟수 · 기간 확정 필요). 탈퇴해도 남김
CREATE TABLE safety_strikes (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       bigint      NOT NULL REFERENCES users (id),
    connection_id bigint,                                 -- 다른 모듈 테이블이라 FK 아님
    request_id    uuid        NOT NULL,
    category      text        NOT NULL,                   -- 판정이 붙인 종류 (기준 확정 필요)
    created_at    timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, request_id)
);
CREATE INDEX idx_safety_strikes_user ON safety_strikes (user_id, created_at);
