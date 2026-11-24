-- 모듈: user (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 알림 (schema.sql 2026-11-24, api.md 43 · 44, ADR 0035). 다른 모듈의 일(단계 상승 · 출석 · 초대 보상)을 user가 받아 적는다
CREATE TABLE notifications (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     bigint      NOT NULL REFERENCES users (id),
    type        text        NOT NULL CHECK (type IN ('STAGE_UP', 'ATTENDANCE', 'REFERRAL_REWARD')),
    title       text        NOT NULL,
    body        text,
    link        text,
    dedup_key   text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    read_at     timestamptz
);
CREATE INDEX idx_notifications_user ON notifications (user_id, id DESC);
CREATE UNIQUE INDEX uq_notifications_dedup ON notifications (user_id, type, dedup_key) WHERE dedup_key IS NOT NULL;
