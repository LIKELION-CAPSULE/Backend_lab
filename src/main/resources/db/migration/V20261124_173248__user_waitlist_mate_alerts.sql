-- 모듈: user (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 출시 알림 신청 (schema.sql 2026-11-24, api.md 45). 정원이 찬 뒤 로그인 없이 이메일만 받음
CREATE TABLE beta_waitlist (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email       text        NOT NULL UNIQUE,
    created_at  timestamptz NOT NULL DEFAULT now()
);

-- 준비 중 메이트 공개 알림 신청 (api.md 46). 메이트는 다른 모듈 테이블이라 FK 아님
CREATE TABLE mate_alerts (
    user_id      bigint      NOT NULL REFERENCES users (id),
    character_id bigint      NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, character_id)
);
