-- 모듈: user (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 12/1 베타 베타 피드백 (schema.sql 2026-11-23, api.md 42). RATING은 사용자당 하루(오전 5시 기준) 1번. 탈퇴해도 남김
CREATE TABLE beta_feedback (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       bigint      NOT NULL REFERENCES users (id),
    kind          text        NOT NULL CHECK (kind IN ('RATING', 'BUG', 'SUGGESTION')),
    score         int         CHECK (score BETWEEN 1 AND 4),
    text          text        CHECK (char_length(text) <= 1000),
    connection_id bigint,
    feedback_day  date        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CHECK ((kind = 'RATING') = (score IS NOT NULL)),
    CHECK (kind = 'RATING' OR text IS NOT NULL)
);
CREATE UNIQUE INDEX uq_beta_feedback_rating ON beta_feedback (user_id, feedback_day) WHERE kind = 'RATING';
