-- 모듈: user (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 12/1 베타 8단계 친구 초대 (schema.sql 2026-11-23, api.md 35 · 36). 받은 사람(invitee)당 한 행. 탈퇴해도 남김
CREATE TABLE referrals (
    id                      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inviter_id              bigint      NOT NULL REFERENCES users (id),
    invitee_id              bigint      NOT NULL UNIQUE REFERENCES users (id),
    device_hash             text        NOT NULL,
    entered_at              timestamptz NOT NULL DEFAULT now(),
    qualified_at            timestamptz,
    qualified_connection_id bigint,
    inviter_rewarded        boolean     NOT NULL DEFAULT false,
    invitee_rewarded        boolean     NOT NULL DEFAULT false,
    CHECK (inviter_id <> invitee_id),
    CHECK ((qualified_at IS NULL) = (qualified_connection_id IS NULL)),
    CHECK (qualified_at IS NOT NULL OR NOT (inviter_rewarded OR invitee_rewarded))
);
CREATE INDEX idx_referrals_inviter ON referrals (inviter_id, id);

