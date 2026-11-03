-- 모듈: user (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 출처: docs/specs/schema.sql 섹션 1·2·6 (2026-11-03 노션 스냅샷). users.avatar_id FK는 avatars 생성 뒤 user_avatar_fk에서.

-- ---------------------------------------------------------------------
-- 1. 회원 / 로그인 (S01)
-- ---------------------------------------------------------------------
-- 이메일 가입자: email, password가 있음 / 소셜 가입자: 둘 다 NULL, user_social_accounts에 행이 있음
-- 이메일은 인증을 먼저 받고 가입시키므로 인증 여부 컬럼은 없음
CREATE TABLE users (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nickname          text        NOT NULL,
    email             text        UNIQUE CHECK (email = lower(email)),  -- 이메일 가입자의 로그인 아이디
    password          text,                                             -- bcrypt 해시 (원문 저장 금지)
    avatar_id         bigint,                                           -- 처음 설정 전에는 NULL (FK는 avatars 생성 후)
    profile_image_url text,
    last_login_at     timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    withdrawn_at      timestamptz,                                      -- NULL이면 활성 회원
    CHECK ((email IS NULL) = (password IS NULL))                        -- 둘 다 있거나 둘 다 없거나
);

-- 소셜 가입 계정 (지금은 계정당 하나, 탈퇴하면 행 삭제 → 같은 소셜 계정으로 재가입 가능)
CREATE TABLE user_social_accounts (
    provider         text        NOT NULL CHECK (provider IN ('KAKAO', 'NAVER', 'APPLE', 'GOOGLE')),
    provider_user_id text        NOT NULL,
    user_id          bigint      NOT NULL UNIQUE REFERENCES users (id),  -- 계정 연결이 생기면 UNIQUE만 제거
    email            text,                                               -- 제공자가 준 이메일 (선택 동의라 없을 수 있음)
    created_at       timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (provider, provider_user_id)
);

-- ---------------------------------------------------------------------
-- 2. 약관 (S01)
-- ---------------------------------------------------------------------
CREATE TABLE terms (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        text        NOT NULL UNIQUE,      -- SERVICE, PRIVACY, AGE_14, MARKETING
    name        text        NOT NULL,
    is_required boolean     NOT NULL,
    sort_order  int         NOT NULL DEFAULT 0,   -- 가입 화면 표시 순서
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE term_versions (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    term_id            bigint      NOT NULL REFERENCES terms (id),
    version            int         NOT NULL,
    content            text        NOT NULL,
    requires_reconsent boolean     NOT NULL DEFAULT true,   -- false면 공지만, 재동의 안 받음
    effective_at       timestamptz NOT NULL,                -- 시행일
    created_at         timestamptz NOT NULL DEFAULT now(),
    UNIQUE (term_id, version)
);
CREATE INDEX idx_term_versions_latest ON term_versions (term_id, effective_at DESC);

-- 동의 기록: 추가만 하고 수정·삭제하지 않음. 철회도 agreed=false인 새 행. 탈퇴해도 남김
CREATE TABLE user_terms (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         bigint      NOT NULL REFERENCES users (id),
    term_version_id bigint      NOT NULL REFERENCES term_versions (id),
    agreed          boolean     NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now()      -- 동의·철회 시각
);
CREATE INDEX idx_user_terms_user_version ON user_terms (user_id, term_version_id, id DESC);

-- ---------------------------------------------------------------------
-- 6. (일부) 기억
-- ---------------------------------------------------------------------
-- 시험, 목표, 약속 (캐릭터 공통). 사용자가 직접 말한 내용이라 탈퇴 시 서버가 삭제
-- 날짜만 말하면 due_time은 NULL (자정과 구분됨). date / time은 타임존 변환 없이 한국 날짜 그대로 저장
CREATE TABLE user_memories (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    bigint      NOT NULL REFERENCES users (id),
    type       text        NOT NULL CHECK (type IN ('EXAM', 'GOAL', 'PROMISE')),
    title      text        NOT NULL,
    due_date   date,                                       -- 시험일, 약속일, 목표 마감일 (없을 수 있음)
    due_time   time,                                       -- NULL이면 시간 미입력
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (due_time IS NULL OR due_date IS NOT NULL)       -- 날짜 없이 시간만 있는 경우 방지
);
CREATE INDEX idx_user_memories_user ON user_memories (user_id, type);
