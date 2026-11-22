-- 모듈: user (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 12/1 베타 1단계 (schema.sql 2026-11-23): 회원 칸 추가 · 가입 기기. 추가만 한다 (user_memories는 대화 단계에서 지움)
ALTER TABLE users
    ADD COLUMN birth_date    date,
    ADD COLUMN honorific     text CHECK (char_length(honorific) BETWEEN 1 AND 10),
    ADD COLUMN about_me      text CHECK (char_length(about_me) <= 60),
    ADD COLUMN referral_code text UNIQUE,
    ADD COLUMN settings      jsonb NOT NULL DEFAULT '{}';

-- 이미 가입한 사람의 초대 코드. id로 만들어 겹치지 않는다 (새 가입자는 코드에서 무작위로 만들고 겹치면 다시 뽑음)
UPDATE users SET referral_code = 'CAP-' || upper(lpad(to_hex(id), 4, '0')) WHERE referral_code IS NULL;

CREATE TABLE signup_devices (
    device_hash text        NOT NULL,
    user_id     bigint      NOT NULL REFERENCES users (id),
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (device_hash, user_id)
);
CREATE INDEX idx_signup_devices_user ON signup_devices (user_id);

