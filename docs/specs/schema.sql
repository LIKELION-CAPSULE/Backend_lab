-- =====================================================================
-- DB 스키마 설계 원본 (ADR 0013). 노션 DB 페이지는 읽기 전용 사본 (https://app.notion.com/p/40db9a07e59280abad14da2dde00c864)
-- 고칠 때는 이 파일만 바꾸는 작은 명세 PR → 머지 뒤 spec-sync로 노션에 반영. 실제 DB 변경은 scripts/new-migration.sh.
-- 노션에는 이 안내 주석(맨 위 5줄)을 뺀 아래 내용이 들어간다.
-- =====================================================================
-- =====================================================================
-- CAPSULE DB schema (PostgreSQL 15 이상)
-- 2026-11-02 최종본: 로그인, 약관, Live2D, 룸·세션, 관계·해금, 대화 전체
--
-- 이번 버전의 원칙
--   - 같은 정보를 두 번 저장하지 않음 (status 대신 withdrawn_at / ended_at)
--   - 탈퇴는 소프트 삭제: users 행은 남기고 개인정보만 비움 → users FK에 CASCADE 없음
--   - 토큰(리프레시, 이메일 인증, 비밀번호 재설정)과 로그인 잠금은 Redis에서 처리
--   - 계산 로직과 묶인 값 목록은 CHECK, 연출용으로 늘어날 목록은 서버 enum으로 관리
--
-- 2026-11-02 변경
--   - characters.personality: text → text[] (태그 배열)
--   - user_memories: start_at / end_at → due_date / due_time
--   - study_sessions: detect_mode 제거
--   - session_events: type에서 GAZE_AWAY 제거
--   - relation_logs: session_event_id, reason 'SESSION_EVENT' 추가 (이벤트별 호감도 반영)
--
-- 2026-11-03 변경
--   - study_sessions.closing_line 추가 (완료 대사 저장. 리포트 새로고침 · 카드에서 같은 문장)
--   - 주석만 정리 (구조 변경 없음): live2d_reaction_maps trigger_type · intensity, study_sessions 시간 컬럼 저장 방식, chat_messages 삭제 조건
--
-- 2026-11-04 변경
--   - session_events.type에 동작 이벤트 DROWSY · YAWN · WAVE 추가 (졸음 · 하품 · 손 흔들기. 상태를 바꾸지 않음)
--   - session_events.type에 동작 이벤트 HEAD_TURN 추가 (고개 돌림. 다른 동작 이벤트와 같이 상태 · 시간 계산에 안 씀)
--   - 주석: 동작 이벤트 서버 쿨다운(같은 type 1분), live2d_reaction_maps.trigger_type에 감정 이름 (API 19 · 20의 감정 동작. 목록은 데이터, 디자이너 에셋에 따라 바뀜)
--   - 호감도 최종안 (ADR 0022): 호감도와 레벨을 나눔
--     user_character_relations: relation_points 0~100 · 기본 50, level = 자리 지킴 시간 레벨, total_present_seconds · streak_days · last_study_date 추가
--     relation_logs: signal 추가(CHAT의 말의 종류), CHAT 범위 −100~5 (비율 감점), 공부 시간 · 첫 만남 점수는 더 기록하지 않음
--     relation_settlements 추가 (공부 종료 때 레벨 전후, 리포트 재조회용)
--   - 대화 기억 세 겹 (ADR 0022): character_memories(아는 사실 최대 20개) · conversation_recaps(세션 카드 최근 2장) 추가,
--     chat_messages.signal 추가, conversations.summary는 더 쓰지 않음 (삭제는 다음 단계), llm_usages.purpose에 MEMORY
--   - 세계관 · 캐릭터 설정을 데이터로 (ADR 0022 결정 15): worlds · world_lore · world_events · character_profiles
--     · character_moves · character_lines · character_scores · character_habits 추가. characters는 화면용 정보만
--   - relation_logs.reason에 ADJUST 추가 (local 흐름 테스트에서 호감도를 손으로 맞춘 만큼)
--
-- 2026-11-05 변경
--   - 대화 엔진(ENGINE 파이프라인) 제거 (ADR 0022 결정 19부터 안 씀): dialogue_states · llm_lab_sessions.state 삭제,
--     llm_lab_sessions · llm_eval_runs의 pipeline에서 ENGINE 제거, engine.* 프롬프트 버전 삭제
--   - 세계관 · 대화 기술 제거 (결정 19부터 안 씀): worlds · world_lore · world_events · character_moves ·
--     character_profiles.world_id 삭제, 안 쓰던 conversations.summary · summary_updated_at 삭제,
--     character_profiles.content_version 추가 (profile.yml version이 더 높으면 시작할 때 다시 불러옴)
-- =====================================================================

BEGIN;

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
-- 3. Live2D 모델 (파일 정보만. 캐릭터와 아바타가 이 모델을 가리킴)
-- ---------------------------------------------------------------------
CREATE TABLE live2d_models (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    storage_prefix  text         NOT NULL,                -- 예: live2d/chaerin/v1/
    model_json_path text         NOT NULL,                -- 예: chaerin.model3.json (prefix 기준 상대 경로)
    scale           numeric(5,2) NOT NULL DEFAULT 1.00,   -- 칸 안에서의 기본 확대율
    offset_x        int          NOT NULL DEFAULT 0,      -- 칸 안에서의 위치 보정
    offset_y        int          NOT NULL DEFAULT 0,
    created_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE live2d_motions (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    model_id   bigint NOT NULL REFERENCES live2d_models (id) ON DELETE CASCADE,
    group_name text   NOT NULL,                  -- model3.json의 Motions 그룹 (Idle, TapBody ...)
    name       text   NOT NULL,                  -- 사람이 알아볼 이름 (예: 손 흔들기)
    file_path  text   NOT NULL,                  -- .motion3.json 상대 경로
    UNIQUE (model_id, file_path),
    UNIQUE (id, model_id)                        -- 반응 매핑의 복합 FK용
);

CREATE TABLE live2d_expressions (
    id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    model_id  bigint NOT NULL REFERENCES live2d_models (id) ON DELETE CASCADE,
    name      text   NOT NULL,                   -- 예: 삐짐, 웃음
    file_path text   NOT NULL,                   -- .exp3.json 상대 경로
    UNIQUE (model_id, file_path),
    UNIQUE (id, model_id)
);

-- 사용자 상태 → 어떤 모션/표정을 틀지 (캐릭터, 아바타 모두)
-- 같은 트리거에 여러 행이면 서버가 똑같은 확률로 하나 고름. 대사는 LLM이 실시간 생성
CREATE TABLE live2d_reaction_maps (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    model_id      bigint   NOT NULL REFERENCES live2d_models (id) ON DELETE CASCADE,
    trigger_type  text     NOT NULL,   -- 허용 값은 서버 enum으로 관리 (지금: GREETING, AWAY, RETURN, BREAK, BREAK_END, DROWSY, YAWN, WAVE, HEAD_TURN = API 18의 '알아채는' 동작용 trigger / 감정 이름(예: HAPPY, SAD) = API 19 · 20의 감정 동작. 감정 목록은 서버 enum이 아니라 이 테이블의 데이터로 정함(디자이너 에셋에 따라 바뀜))
    intensity     smallint CHECK (intensity BETWEEN 1 AND 3),   -- AWAY의 눈치 / 말 걸기 / 서운함. NULL이면 강도 무관 (그 강도 행이 없을 때도 씀)
    motion_id     bigint,
    expression_id bigint,
    CHECK (motion_id IS NOT NULL OR expression_id IS NOT NULL),  -- 모션이나 표정 중 하나는 있어야 함
    FOREIGN KEY (motion_id, model_id)
        REFERENCES live2d_motions (id, model_id) ON DELETE CASCADE,       -- 같은 모델의 모션만
    FOREIGN KEY (expression_id, model_id)
        REFERENCES live2d_expressions (id, model_id) ON DELETE CASCADE    -- 같은 모델의 표정만
);
CREATE INDEX idx_live2d_reaction_lookup
    ON live2d_reaction_maps (model_id, trigger_type, intensity);

-- ---------------------------------------------------------------------
-- 4. 아바타 (S02)
-- ---------------------------------------------------------------------
CREATE TABLE avatars (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    model_id      bigint      NOT NULL UNIQUE REFERENCES live2d_models (id),
    name          text        NOT NULL,                  -- 예: 아바타 A
    thumbnail_url text        NOT NULL,                  -- 선택 화면 카드. 이미지 칸(*_url)은 CDN 상대 경로(예: haru/thumbnail.png) 또는 전체 URL. 상대 경로면 서버가 CDN 주소를 붙여 내려줌 (2026-11-05)
    sort_order    int         NOT NULL DEFAULT 0,
    is_active     boolean     NOT NULL DEFAULT true,     -- 고른 사용자가 있으니 지우지 말고 비활성화
    created_at    timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE users
    ADD FOREIGN KEY (avatar_id) REFERENCES avatars (id);

-- ---------------------------------------------------------------------
-- 5. 스터디룸 / 배경 / 캐릭터 (S03, S04)
-- ---------------------------------------------------------------------
-- 1:1 / 1:3 구분은 저장하지 않음: 룸에 속한 캐릭터 수로 계산
CREATE TABLE study_rooms (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          text        NOT NULL,
    description   text,
    thumbnail_url text,                                  -- 홈 포스터 카드. CDN 상대 경로 또는 전체 URL (avatars.thumbnail_url과 같은 규칙)
    banner_url    text,                                  -- 룸 상세(H04) 가로 배너. 같은 규칙. NULL이면 프론트가 thumbnail_url (2026-11-05)
    sort_order    int         NOT NULL DEFAULT 0,
    is_active     boolean     NOT NULL DEFAULT true,
    created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE room_backgrounds (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_id      bigint  NOT NULL REFERENCES study_rooms (id) ON DELETE CASCADE,
    name         text    NOT NULL,
    image_url    text    NOT NULL,                          -- CDN 상대 경로 또는 전체 URL (avatars.thumbnail_url과 같은 규칙)
    is_default   boolean NOT NULL DEFAULT false,
    unlock_type  text    CHECK (unlock_type IN ('LEVEL')),  -- NULL이면 처음부터 열림 (구매는 BM 확정 후 추가)
    unlock_value int,                                       -- LEVEL이면 이 룸 캐릭터와의 필요 관계 단계
    sort_order   int     NOT NULL DEFAULT 0,
    is_active    boolean NOT NULL DEFAULT true,
    UNIQUE (id, room_id),                                   -- 세션의 복합 FK용
    CHECK ((unlock_type IS NULL) = (unlock_value IS NULL)),
    CHECK (NOT is_default OR unlock_type IS NULL)           -- 기본 배경은 항상 열려 있어야 함
);
CREATE INDEX idx_room_backgrounds_room ON room_backgrounds (room_id, sort_order);
CREATE UNIQUE INDEX uq_room_backgrounds_default
    ON room_backgrounds (room_id) WHERE is_default;         -- 룸당 기본 배경 하나

-- 캐릭터는 한 룸에만 속하고, 모델은 하나
CREATE TABLE characters (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_id           bigint      NOT NULL REFERENCES study_rooms (id),
    model_id          bigint      NOT NULL UNIQUE REFERENCES live2d_models (id),
    name              text        NOT NULL,
    personality       text[]      NOT NULL DEFAULT '{}',   -- 성격 태그 (예: {츤데레, 슬리데린 5학년, 라이벌}), 순서대로 표시
    intro             text,
    profile_image_url text,                                -- CDN 상대 경로 또는 전체 URL (avatars.thumbnail_url과 같은 규칙)
    sort_order        int         NOT NULL DEFAULT 0,      -- 룸 안 표시 순서
    is_active         boolean     NOT NULL DEFAULT true,
    created_at        timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id, room_id)                                   -- session_characters 복합 FK용
);
CREATE INDEX idx_characters_room ON characters (room_id, sort_order);

-- 사용자가 연 배경 (해금 판정 규칙은 서버: 한 명이라도 / 모두 / 합계 중 미정)
CREATE TABLE user_unlocked_backgrounds (
    user_id       bigint      NOT NULL REFERENCES users (id),
    background_id bigint      NOT NULL REFERENCES room_backgrounds (id) ON DELETE CASCADE,
    unlocked_at   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, background_id)
);

-- ---------------------------------------------------------------------
-- 6. 캐릭터 콘텐츠 / 관계 / 해금 / 기억 (S04, S09, S11)
-- ---------------------------------------------------------------------
CREATE TABLE character_contents (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id   bigint  NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    type           text    NOT NULL CHECK (type IN ('LINE', 'MOTION', 'STORY', 'VOICE')),
    title          text    NOT NULL,
    body           text,                                   -- 대사, 스토리 본문
    asset_url      text,                                   -- 미리 녹음한 음성 파일 등
    motion_id      bigint  REFERENCES live2d_motions (id), -- MOTION일 때 (그 캐릭터 모델의 모션인지는 서버가 검사)
    unlock_type    text    NOT NULL DEFAULT 'LEVEL' CHECK (unlock_type IN ('LEVEL', 'EVENT')),  -- 구매는 BM 확정 후
    unlock_value   int,                                    -- LEVEL이면 필요한 관계 단계
    event_start_at timestamptz,                            -- EVENT면 기간
    event_end_at   timestamptz,
    is_active      boolean NOT NULL DEFAULT true,
    CHECK ((type = 'MOTION') = (motion_id IS NOT NULL)),
    CHECK ((unlock_type = 'LEVEL') = (unlock_value IS NOT NULL)),
    CHECK ((unlock_type = 'EVENT') = (event_start_at IS NOT NULL)),
    CHECK (event_end_at IS NULL OR event_end_at >= event_start_at)
);
CREATE INDEX idx_character_contents_character ON character_contents (character_id, type);

-- 사용자 × 캐릭터 관계: 처음 만날 때 행을 만듦. 행이 없으면 아직 안 만난 것
-- 호감도는 세션이 끝나도 여기에 계속 남음 (사용자마다 다르므로 characters가 아니라 여기)
-- 호감도(캐릭터가 나를 어떻게 보는가)와 레벨(함께 공부한 시간)은 따로: 관계 단계 = 호감도 구간, 레벨 = 자리 지킴 시간
CREATE TABLE user_character_relations (
    id                    bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id               bigint      NOT NULL REFERENCES users (id),
    character_id          bigint      NOT NULL REFERENCES characters (id),
    relation_points       int         NOT NULL DEFAULT 50    -- 호감도 0~100, 50에서 시작. 대화 · 자리 비움 · 연속 출석으로만 바뀜. 범위를 넘는 만큼은 잘라 반영
                                      CHECK (relation_points BETWEEN 0 AND 100),
    level                 int         NOT NULL DEFAULT 1,    -- 레벨 = 1 + 자리 지킴 시간 ÷ 60분 (계산값을 같이 저장, 상한 없음)
    total_study_seconds   int         NOT NULL DEFAULT 0,    -- 함께한 공부 시간 (쉬는 시간 제외, 세션 합계와 같아야 함)
    total_present_seconds int         NOT NULL DEFAULT 0 CHECK (total_present_seconds >= 0),  -- 그중 자리 지킴 (레벨 기준)
    streak_days           int         NOT NULL DEFAULT 0 CHECK (streak_days >= 0),            -- 연속 출석 일수 (한국 날짜)
    last_study_date       date,                              -- 마지막으로 공부를 시작한 날 (한국 날짜)
    first_met_at          timestamptz NOT NULL DEFAULT now(),
    last_met_at           timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, character_id)
);
CREATE INDEX idx_relations_character ON user_character_relations (character_id);

CREATE TABLE user_unlocked_contents (
    user_id     bigint      NOT NULL REFERENCES users (id),
    content_id  bigint      NOT NULL REFERENCES character_contents (id) ON DELETE CASCADE,
    unlocked_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, content_id)
);

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
    heartbeat_study_seconds   int,                        -- 마지막 heartbeat 때 프론트가 보낸 공부 시간 (API 21, 2026-11-04 추가). 자동 마감에 사용
    heartbeat_present_seconds int,                        -- 위 값 중 자리 지킴
    focus_seconds     int         NOT NULL DEFAULT 0,     -- 아래 넷은 종료 때 저장. focus · distract는 정상 종료면 프론트 값(API 22), 자동 마감이면 마지막 heartbeat 값(없으면 session_events로 계산). break · return_count는 항상 session_events로 계산
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

-- 상태 이벤트는 상태가 바뀔 때만 한 줄씩. 각 상태는 다음 상태 이벤트가 올 때 끝남
--   FOCUS: 시작·쉬는 시간 종료 / AWAY: 자리 비움 / RETURN: 딴짓에서 복귀 / BREAK: 쉬는 시간
-- 동작 이벤트는 감지된 순간 한 줄. 상태를 바꾸지 않고 시간 계산에 쓰지 않음 (캐릭터 반응 · 호감도용)
--   DROWSY: 졸음 / YAWN: 하품 / WAVE: 손 흔들기 / HEAD_TURN: 고개 돌림
--   쉬는 시간 중에는 감지 이벤트(AWAY · RETURN · 동작 이벤트)를 남기지 않음 (서버가 무시)
--   같은 동작 type이 1분 안에 다시 오면 남기지 않음 (서버 쿨다운)
CREATE TABLE session_events (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id     bigint      NOT NULL REFERENCES study_sessions (id) ON DELETE CASCADE,
    type           text        NOT NULL CHECK (type IN ('FOCUS', 'AWAY', 'RETURN', 'BREAK', 'DROWSY', 'YAWN', 'WAVE', 'HEAD_TURN')),
    false_positive boolean     NOT NULL DEFAULT false,   -- '아니야' 버튼 (오탐이면 집중으로 계산)
    occurred_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_session_events_session ON session_events (session_id, occurred_at);

-- 호감도 변화 이력 (계속 남음). delta는 실제로 바뀐 만큼(0~100 밖으로 나간 몫은 잘림) → 50 + 기록의 합 = 지금 점수
--   CHAT: 말 패턴으로 고른 말의 종류(signal) → 캐릭터 점수표 → 오르는 쪽만 하루 상한. 심한 말은 지금 값의 %로 깎음
--   SESSION_EVENT: 자리 비움을 끝낸 상태 이벤트에서, 1분 넘게 비웠으면 감점 (하루 상한). 1:3 룸이면 캐릭터마다 한 행씩
--   STREAK: 연속 출석 (2일째부터 하루 한 번, 세션 첫 FOCUS)
--   EVENT: 기간 한정 이벤트(character_contents의 EVENT)로 받은 점수
--   STUDY_TIME · FIRST_MEET · FOCUS_BONUS: 더 기록하지 않음 (호감도 최종안. 공부 시간은 레벨에만)
--   ADJUST: 손으로 맞춘 점수 (local 흐름 테스트의 '호감도를 N으로'). 지금 점수와 기록의 합이 계속 맞게 맞춘 만큼을 남김
CREATE TABLE relation_logs (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    relation_id      bigint      NOT NULL REFERENCES user_character_relations (id),
    session_id       bigint      REFERENCES study_sessions (id),
    session_event_id bigint      REFERENCES session_events (id),   -- SESSION_EVENT일 때 원인 이벤트
    request_id       uuid,                                      -- CHAT일 때 메시지 전송 요청 id (메시지는 지워지므로 FK 아님)
    signal           text,                                      -- CHAT일 때 말의 종류 (COMPLIMENT, HATE_OR_SEXUAL 등). 그날 오름 멈춤 · 문의 대응
    delta            int         NOT NULL,                      -- 음수 가능
    reason           text        NOT NULL CHECK (reason IN (
                         'STUDY_TIME', 'STREAK', 'FOCUS_BONUS', 'FIRST_MEET', 'EVENT', 'CHAT', 'SESSION_EVENT', 'ADJUST')),
    created_at       timestamptz NOT NULL DEFAULT now(),
    CHECK ((reason = 'CHAT') = (request_id IS NOT NULL)),
    CHECK ((reason = 'SESSION_EVENT') = (session_event_id IS NOT NULL)),
    CHECK (delta <> 0),                                        -- 0점이면 기록하지 않음
    CONSTRAINT relation_logs_chat_range
        CHECK (reason <> 'CHAT' OR delta BETWEEN -100 AND 5)   -- 대화 한 번: 오르는 쪽은 작게, 비율 감점은 크게
);
CREATE INDEX idx_relation_logs_relation ON relation_logs (relation_id, created_at DESC);
CREATE UNIQUE INDEX uq_relation_logs_request
    ON relation_logs (relation_id, request_id) WHERE request_id IS NOT NULL;              -- 같은 요청은 한 번만 반영
CREATE UNIQUE INDEX uq_relation_logs_session_event
    ON relation_logs (relation_id, session_event_id) WHERE session_event_id IS NOT NULL;  -- 같은 이벤트는 캐릭터마다 한 번만

-- 공부 종료 정산 때의 레벨 (세션마다 한 번). 리포트를 다시 볼 때 같은 값을 주려고 남긴다 (레벨은 점수 기록으로 다시 계산할 수 없음)
CREATE TABLE relation_settlements (
    relation_id     bigint      NOT NULL REFERENCES user_character_relations (id),
    session_id      bigint      NOT NULL REFERENCES study_sessions (id),
    present_seconds int         NOT NULL CHECK (present_seconds >= 0),
    level_before    int         NOT NULL,
    level_after     int         NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (relation_id, session_id)
);

-- ---------------------------------------------------------------------
-- 8. 대화 LLM (S08)
-- ---------------------------------------------------------------------
-- 사용자 × 캐릭터마다 하나. 탈퇴 시 서버가 삭제
-- 대화 기억은 세 겹이고 모두 크기가 정해져 있다: 아는 사실(character_memories) · 지난 세션 흐름(conversation_recaps) · 이번 세션 원문(chat_messages)
CREATE TABLE conversations (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id            bigint      NOT NULL REFERENCES users (id),
    character_id       bigint      NOT NULL REFERENCES characters (id),
    created_at         timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, character_id)
);

-- 지난 세션 흐름: 공부 종료 때 그 세션만 카드 한 장 (예: "11/03 55분, 3단원 하다 지쳐서 일찍 끝냄"). 최근 2장만 남기고 다시 압축하지 않음
CREATE TABLE conversation_recaps (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id bigint      NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    session_id      bigint      NOT NULL,                 -- 다른 모듈(session) 테이블이라 FK 아님
    content         text        NOT NULL CHECK (char_length(content) BETWEEN 1 AND 200),
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (conversation_id, session_id)
);

-- 캐릭터가 사용자에 대해 아는 사실 한 줄씩 (최대 20개). 매 턴 결정 모델이 추가 · 고치기 · 지우기를 고르고, 넘치면 가장 덜 중요한 하나를 버림
-- 답장에는 다 넣지 않는다: 결정 모델이 이번 말에 꺼낼 사실 하나(또는 선택 안 함)를 골라 그것만 넣는다
-- 사용자가 직접 넣는 일정(user_memories)과는 따로. 탈퇴 시 서버가 삭제
CREATE TABLE character_memories (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           bigint      NOT NULL REFERENCES users (id),
    character_id      bigint      NOT NULL REFERENCES characters (id),
    content           text        NOT NULL CHECK (char_length(content) BETWEEN 1 AND 80),
    source_request_id uuid,                               -- 이 기억을 만든 · 고친 메시지 요청 (메시지는 지워지므로 FK 아님)
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_character_memories_owner ON character_memories (user_id, character_id);

-- 이번 세션 원문. 세션이 끝나면 카드에 넣은 뒤 한 트랜잭션에서 삭제 (카드 쓰기에 실패하면 남겨 다음 종료 때 같이 넣음)
-- 음성(TTS)은 저장하지 않고 응답으로 바로 전달
CREATE TABLE chat_messages (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id bigint      NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    session_id      bigint      NOT NULL REFERENCES study_sessions (id),
    request_id      uuid        NOT NULL,                 -- 사용자 메시지와 그 답변이 같은 값
    sender          text        NOT NULL CHECK (sender IN ('USER', 'CHARACTER')),
    content         text        NOT NULL,
    signal          text,                                 -- USER일 때 말의 종류 (같은 세션 안 반복 규칙: 고민 1번, 무례 반복, 칭찬 연타)
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (conversation_id, request_id, sender)          -- 재전송돼도 질문·답변 각각 한 번만
);
CREATE INDEX idx_chat_messages_session ON chat_messages (session_id, id);

-- LLM 호출 기록 (지우지 않고 계속 쌓임, 비용·크레딧 근거 + 품질·지연 분석). 호출 한 번 = 한 행, 실패도 기록
-- 실험실·평가 호출은 사용자·세션이 없다 (source LAB · EVAL)
CREATE TABLE llm_usages (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id            bigint         REFERENCES users (id),
    session_id         bigint         REFERENCES study_sessions (id),
    purpose            text           NOT NULL CHECK (purpose IN (
                           'CHAT', 'DIRECTOR', 'ACTOR', 'JUDGE', 'REACTION', 'SUMMARY', 'CLOSING', 'EVAL_JUDGE', 'DECIDE', 'MEMORY')),
    source             text           NOT NULL CHECK (source IN ('APP', 'LAB', 'EVAL')),
    trace_id           uuid           NOT NULL,           -- 한 요청(대화 한 턴 등)에서 나간 호출을 묶음
    provider           text           NOT NULL,           -- anthropic · openai · jev · fake
    model              text           NOT NULL,
    status             text           NOT NULL CHECK (status IN ('OK', 'ERROR')),
    error              text,
    stop_reason        text,
    input_tokens       int            NOT NULL CHECK (input_tokens >= 0),        -- 캐시에 없던 입력
    output_tokens      int            NOT NULL CHECK (output_tokens >= 0),
    cache_read_tokens  int            NOT NULL CHECK (cache_read_tokens >= 0),
    cache_write_tokens int            NOT NULL CHECK (cache_write_tokens >= 0),
    cost_usd           numeric(12, 6) NOT NULL,
    latency_ms         int            NOT NULL,
    ttft_ms            int,                               -- 스트리밍일 때 첫 글자까지
    prompt_version_ids bigint[]       NOT NULL,           -- 이 호출에 쓴 prompt_versions.id
    request            jsonb,                             -- 보낸 프롬프트 원문: 실험실·평가, 또는 설정으로 켰을 때만
    response           text,
    created_at         timestamptz    NOT NULL DEFAULT now()
);
CREATE INDEX idx_llm_usages_user ON llm_usages (user_id, created_at);
CREATE INDEX idx_llm_usages_trace ON llm_usages (trace_id);

-- 캐릭터 설정 (캐릭터당 하나, 대사를 만드는 데 쓰는 데이터, ADR 0022 결정 15). 캐릭터를 더해도 코드는 바뀌지 않는다
-- characters(6장)는 화면용 정보만 둔다. 처음 값은 git의 classpath:characters/<code>/profile.yml에서 불러온다
CREATE TABLE character_profiles (
    character_id         bigint      PRIMARY KEY REFERENCES characters (id),
    code                text        NOT NULL UNIQUE,    -- 예: malfoy
    speaker              text        NOT NULL,           -- 대사에서 부르는 이름
    bible                text        NOT NULL,           -- 정체성 · 화법 · 대화 원칙 · 형식
    rules                text        NOT NULL,           -- 절대 규칙
    safety               text        NOT NULL CHECK (safety IN ('MINOR', 'ADULT')),  -- 미성년 설정이면 연애 · 성적 표현 금지
    expressions          text[]      NOT NULL,           -- 고를 수 있는 표정 (19 · 20 응답 emotion 값)
    expression_fallbacks jsonb       NOT NULL DEFAULT '{}',  -- 대체 표: 목록 안 키 = 에셋이 없는 표정 → 대신 쓸 표정, 목록 밖 키 = 모델이 쓴 감정 이름 → 목록 안 표정 (예: {"걱정": "집중"})
    stage_tones          jsonb       NOT NULL DEFAULT '{}',  -- 관계 단계 → 그 단계의 관계 설명 (거리감 · 호칭 · 챙김 수위, ADR 0022 결정 19)
    study_style          text        NOT NULL,           -- 공부 중 답 길이 · 구성 (예: 1~2문장, 공부 얘기 중심)
    break_style          text        NOT NULL,           -- 쉬는 시간 답 길이 · 구성 (예: 2~4문장, 공부 재촉 없이 수다)
    samples              jsonb       NOT NULL DEFAULT '[]',  -- 관계 단계별 말투 샘플 [{"minStage", "user", "reply"}] (ADR 0022 결정 19)
    content_version      integer     NOT NULL DEFAULT 0,     -- 불러온 profile.yml의 version. 파일 쪽이 더 높으면 앱 시작 때 파일로 다시 불러온다 (실험실에서 고친 값은 덮임)
    updated_at           timestamptz NOT NULL DEFAULT now()
);

-- 상황별 미리 쓴 대사: 같은 상황이면 sort 순서대로 돌려 쓴다
--   situation: GREETING · AWAY · AWAY_2 · AWAY_3 · RETURN · BREAK · BREAK_END · DROWSY · YAWN · WAVE · HEAD_TURN · REPLY_FALLBACK · CLOSING
--              · GIVE_UP(공부 포기 완료 대사) · TODAY(캐릭터의 오늘 근황, sort = 요일 월 0 ~ 일 6)
CREATE TABLE character_lines (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id bigint NOT NULL REFERENCES character_profiles (character_id) ON DELETE CASCADE,
    situation    text   NOT NULL,
    min_stage    int    NOT NULL DEFAULT 1 CHECK (min_stage >= 1),  -- 이 관계 단계부터
    sort         int    NOT NULL DEFAULT 0,
    text         text   NOT NULL,                        -- {name}은 닉네임으로 바뀜
    expression   text
);
CREATE INDEX idx_character_lines_situation ON character_lines (character_id, situation, sort);

-- 대화 점수표 (말의 종류별). 없으면 서버 설정의 기본 표
CREATE TABLE character_scores (
    character_id     bigint  NOT NULL REFERENCES character_profiles (character_id) ON DELETE CASCADE,
    signal           text    NOT NULL,
    points           int     NOT NULL,
    loss_percent     int     NOT NULL DEFAULT 0 CHECK (loss_percent BETWEEN 0 AND 100),
    once_per_session boolean NOT NULL DEFAULT false,
    repeat_points    int,
    PRIMARY KEY (character_id, signal)
);

-- 피할 말버릇 (예: 첫마디 금지, 세션에 한 번)
CREATE TABLE character_habits (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id bigint NOT NULL REFERENCES character_profiles (character_id) ON DELETE CASCADE,
    phrase       text   NOT NULL,
    rule         text   NOT NULL CHECK (rule IN ('NO_OPENING', 'ONCE_PER_SESSION')),
    UNIQUE (character_id, phrase)
);

-- 프롬프트 버전 (배포 없이 바꾸고 되돌리기). 본문은 고치지 않고 새 버전을 만든다.
-- (키, 캐릭터)마다 ACTIVE는 하나, 새 버전을 켜면 이전 ACTIVE는 ARCHIVED. 캐릭터 전용 ACTIVE가 공통(character_id NULL)보다 먼저
CREATE TABLE prompt_versions (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    prompt_key   text        NOT NULL,                     -- chat.system · fixed.lines · scene.system 등
    character_id bigint      REFERENCES characters (id),
    version      int         NOT NULL CHECK (version > 0),
    status       text        NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    body         text        NOT NULL,
    note         text,                                     -- 무엇을 왜 바꿨는지
    created_at   timestamptz NOT NULL DEFAULT now(),
    activated_at timestamptz,
    UNIQUE NULLS NOT DISTINCT (prompt_key, character_id, version)
);
CREATE UNIQUE INDEX uq_prompt_versions_active ON prompt_versions (prompt_key, character_id) NULLS NOT DISTINCT
    WHERE status = 'ACTIVE';

-- LLM 실험실 (내부 품질 테스트. 서비스 데이터와 섞지 않음)
CREATE TABLE llm_lab_sessions (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title        text        NOT NULL,
    character_id bigint      NOT NULL REFERENCES characters (id),
    pipeline     text        NOT NULL CHECK (pipeline IN ('SIMPLE', 'SCENE')),
    config       jsonb       NOT NULL,                     -- 실험 조건 (공급자·모델·프롬프트 버전 고정·가상 사용자 정보)
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE llm_lab_turns (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lab_session_id bigint         NOT NULL REFERENCES llm_lab_sessions (id) ON DELETE CASCADE,
    trace_id       uuid           NOT NULL,               -- 이 턴의 llm_usages
    user_text      text           NOT NULL,
    reply          text,
    emotion        text,
    affinity       int,
    error          text,
    latency_ms     int            NOT NULL,
    cost_usd       numeric(12, 6) NOT NULL,
    trace          jsonb          NOT NULL,               -- 파이프라인 중간 결과
    rating         int CHECK (rating BETWEEN 1 AND 5),    -- 사람 평가
    rating_note    text,
    created_at     timestamptz    NOT NULL DEFAULT now()
);
CREATE INDEX idx_llm_lab_turns_session ON llm_lab_turns (lab_session_id, id);

-- 평가: 골든 대본(케이스)을 한 조건으로 여러 번 돌려 LLM 심사·사람 점수로 비교
CREATE TABLE llm_eval_cases (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         text        NOT NULL,
    character_id bigint      NOT NULL REFERENCES characters (id),
    turns        jsonb       NOT NULL,                     -- 사용자 말 배열
    context      jsonb       NOT NULL,                     -- 가상 사용자 정보
    rubric       text,                                     -- 심사 기준
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE llm_eval_runs (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    label       text        NOT NULL,
    pipeline    text        NOT NULL CHECK (pipeline IN ('SIMPLE', 'SCENE')),
    config      jsonb       NOT NULL,
    case_ids    bigint[]    NOT NULL,
    repeats     int         NOT NULL CHECK (repeats BETWEEN 1 AND 20),
    judge       boolean     NOT NULL,
    status      text        NOT NULL CHECK (status IN ('RUNNING', 'DONE', 'FAILED')),
    created_at  timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz
);

CREATE TABLE llm_eval_results (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id      bigint         NOT NULL REFERENCES llm_eval_runs (id) ON DELETE CASCADE,
    case_id     bigint         NOT NULL REFERENCES llm_eval_cases (id) ON DELETE CASCADE,
    repeat_no   int            NOT NULL,
    trace_id    uuid           NOT NULL,
    transcript  jsonb          NOT NULL,
    judge_score numeric(4, 1),                            -- LLM 심사 0~10
    judge_note  text,
    human_score int CHECK (human_score BETWEEN 1 AND 5),
    human_note  text,
    latency_ms  int            NOT NULL,
    cost_usd    numeric(12, 6) NOT NULL,
    error       text,
    created_at  timestamptz    NOT NULL DEFAULT now()
);
CREATE INDEX idx_llm_eval_results_run ON llm_eval_results (run_id, case_id, repeat_no);

COMMIT;
