-- 모듈: credit (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 12/1 베타 7단계 (schema.sql 2026-11-23, ADR 0031): 크레딧 지급 건 · 내역. 탈퇴해도 남김
CREATE TABLE credit_grants (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     bigint      NOT NULL REFERENCES users (id),
    kind        text        NOT NULL CHECK (kind IN ('ATTENDANCE', 'INVITE')),
    amount      int         NOT NULL CHECK (amount > 0),
    remaining   int         NOT NULL,
    granted_on  date,
    source      text,
    expires_at  timestamptz,
    expired_at  timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CHECK (remaining BETWEEN 0 AND amount),
    CHECK ((kind = 'ATTENDANCE') = (granted_on IS NOT NULL AND expires_at IS NOT NULL)),
    CHECK ((kind = 'INVITE') = (source IS NOT NULL))
);
CREATE UNIQUE INDEX uq_credit_grants_attendance ON credit_grants (user_id, granted_on) WHERE kind = 'ATTENDANCE';
CREATE UNIQUE INDEX uq_credit_grants_invite ON credit_grants (user_id, source) WHERE kind = 'INVITE';

CREATE TABLE credit_ledger (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       bigint      NOT NULL REFERENCES users (id),
    type          text        NOT NULL CHECK (type IN ('ATTENDANCE', 'INVITE', 'USE', 'EXPIRE')),
    title         text        NOT NULL,
    change        int         NOT NULL CHECK (change <> 0),
    balance_after int         NOT NULL CHECK (balance_after >= 0),
    request_id    uuid,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CHECK ((type = 'USE') = (request_id IS NOT NULL)),
    CHECK ((change > 0) = (type IN ('ATTENDANCE', 'INVITE')))
);
CREATE INDEX idx_credit_ledger_user ON credit_ledger (user_id, id);
CREATE UNIQUE INDEX uq_credit_ledger_request ON credit_ledger (user_id, request_id) WHERE request_id IS NOT NULL;
