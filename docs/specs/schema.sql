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
--   - user_character_relations.last_severe_at 추가: 0점에서 심한 말을 하면 기록(delta 0)이 안 남아 그날 오름 멈춤이 안 걸리던 것
--   - character_profiles.lore 추가 (로어북): 키워드가 사용자 말에 나올 때만 답장 프롬프트에 넣는 원작 설정 카드.
--     지운 world_lore를 캐릭터별 카드로 되살림 (해리 포터를 말해도 반응이 밋밋하던 것)
--
-- 2026-11-06 변경
--   - 운영 설정 (ADR 0026): relation_settings · dialogue_settings 추가. 실험실 '운영 설정' 화면에서 바꾼 칸만 저장하고,
--     나머지는 application.yml 값. 배포 없이 호감도 숫자 · 대화 설정 · AI 공급자와 모델을 바꾼다
--   - 표정은 결정 모델이 고른다 (ADR 0028): character_profiles.expression_fallbacks 삭제(모델이 쓴 목록 밖 감정 이름을 맞추던 대체 표),
--     expression_guide 추가(표정마다 언제 쓰는지. 결정 모델 보기 설명)
--   - character_profiles.lab_edited_at 추가: 실험실에서 고친 캐릭터 설정은 profile.yml 버전이 올라가도 시작할 때 덮지 않는다.
--     실험실에 "파일에 새 버전"을 띄우고, 사람이 [파일 값으로 바꾸기]를 눌러야 파일 값이 들어간다
--
-- 2026-11-23 변경 (12/1 베타 1단계: 회원)
--   - users: birth_date · honorific · about_me · referral_code · settings 추가
--   - signup_devices 추가 (가입 기기, 친구 초대 부정 방지)
--   - user_memories(일정)는 대화 프롬프트가 아직 읽어서 이번에는 두고, 대화 단계에서 지운다
--
-- 2026-11-23 변경 (12/1 베타 2단계: 메이트 데이터)
--   - characters: 메이트 화면 칸 one_liner · hashtags · age_rating · world · my_role · illustration_url · card_image_url
--     · voice_sample_url · stage_names · coming_soon 추가 (추가만. room_id · personality는 스터디룸 정리 때 지움)
--   - live2d_models.format (CUBISM · ALIVING2D_V2), live2d_motions · live2d_expressions.label 추가
--
-- 2026-11-23 변경 (12/1 베타 3단계: 세션 → 연결)
--   - study_sessions → connections (character_id 추가, room_id · background_id · target_minutes 삭제), session_characters 삭제
--   - session_events → connection_events (session_id → connection_id)
--   - 다른 테이블 session_id → connection_id: chat_messages · llm_usages · conversation_recaps · relation_logs · relation_settlements,
--     relation_logs.session_event_id → connection_event_id
--   - 스터디룸 삭제: study_rooms · room_backgrounds · user_unlocked_backgrounds, characters.room_id · personality 삭제,
--     characters.model_id NULL 허용(준비 중 메이트)
--   - 시간 칸(focus · distract · break · heartbeat_study)과 이벤트 종류는 다음 단계(연결 새 기능)에서
--
-- 2026-11-23 변경 (12/1 베타 4단계: 연결 새 기능)
--   - connections: focus_seconds → present_seconds, distract_seconds → away_seconds, heartbeat_study_seconds →
--     heartbeat_connected_seconds, break_seconds 삭제. connected_seconds · camera_on_seconds · pip_seconds ·
--     message_count · mood · heartbeat_camera_on_seconds · heartbeat_pip_seconds 추가. end_type에 AUTO_AWAY · MINOR_LIMIT
--   - connection_events.type: 새 이벤트 PRESENT · IDLE_TALK 추가 (옛 FOCUS · BREAK · 동작 이벤트 행은 기록으로 남기고 API는 받지 않음)
--
-- 2026-11-23 변경 (12/1 베타 5단계: 친밀도 · 해금)
--   - user_character_relations: stage · credited_seconds · total_connected_seconds 추가 (호감도 칸은 대화용으로 그대로)
--   - relation_daily 추가 (하루 인정 시간), relation_settlements에 credited_seconds · stage_before · stage_after
--   - character_contents: 해금 종류 EXPRESSION · VOICE_LINE, unlock_stage · expression_name. 옛 LINE · MOTION · STORY ·
--     VOICE · 이벤트 기간 칸 삭제 (쓰는 코드 · 행 없음)
--
-- 2026-11-23 변경 (12/1 베타 6단계: 대화방 이어가기)
--   - chat_messages: 연결이 끝나도 지우지 않음. recapped_at 추가, 대화방 최신순 인덱스
--   - connections.message_count: 답장을 저장할 때 +1
--
-- 2026-11-23 변경 (12/1 베타 7단계: 크레딧, ADR 0031)
--   - credit_grants · credit_ledger 추가 (새 모듈 credit)
--
-- 2026-11-23 변경 (12/1 베타 8단계: 친구 초대)
--   - referrals 추가 (받은 사람당 한 행, 첫 연결 끝날 때 보상)
--
-- 2026-11-23 변경 (12/1 베타 남은 것: 대화 모드 · 안전 · 평가 · 신고 · 베타 피드백)
--   - chat_messages: input_type · mode · rating · hidden_at
--   - chat_reports · safety_strikes (dialogue), beta_feedback (user) 추가
--
-- 2026-11-24 변경 (음성 TTS, ADR 0034)
--   - chat_messages.voice_charged 추가 (이 답장에서 보이스 크레딧을 깎았는지)
--
-- 2026-11-23 변경 (메이트 배경)
--   - character_backgrounds 추가 (메이트마다 배경 여러 장, 기본 하나). 대화 흐름에 따라 결정 모델이 고른다 (ADR 0032)
--   - connections.background_code 추가 (이 연결의 지금 배경, NULL이면 메이트 기본 배경)
--
-- 2026-11-25 변경 (로그인 화면 메이트, api.md 48)
--   - live2d_models: engine_path · player_path · reactions_path 추가 (Aliving2D 엔진 스크립트 경로)
--
-- 2026-11-25 변경 (메이트 목소리, ADR 0036)
--   - characters: tts_voice · tts_model · tts_instructions 추가 (대화 음성 28과 '목소리 듣기' 샘플이 같은 값으로 만들어짐)
--
-- 2026-11-24 변경 (화면 대조, ADR 0035)
--   - notifications · beta_waitlist · mate_alerts 추가 (user, api.md 43 ~ 46)
--   - connections.notified_stage 추가 (연결 중 단계 상승 알림을 한 번만, api.md 21)
--   - user_memories: 저장 · 조회 API(13 · 14) 삭제. 테이블은 남김 (대화 프롬프트가 남은 행을 읽음)
--
-- 2026-11-24 변경 (모델 형식 기본값)
--   - live2d_models.format 기본값 CUBISM → ALIVING2D_V2. 앞으로 넣는 모델은 Aliving2D 묶음(model.json).
--     지금 있는 Cubism 모델(메이트 말포이 · 아바타)은 CUBISM 그대로(시드 · 데이터에 format을 적어 둠)
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
    birth_date        date,                                             -- 이메일은 가입 때, 카카오 · 베타 전 가입자는 온보딩에서. NULL이면 onboardingRequired
    honorific         text CHECK (char_length(honorific) BETWEEN 1 AND 10),  -- 호칭 (메이트가 부르는 이름)
    about_me          text CHECK (char_length(about_me) <= 60),         -- 나에 대해 한 줄
    referral_code     text        UNIQUE,                               -- 내 초대 코드 (CAP-XXXX). 가입 때 만듦
    settings          jsonb       NOT NULL DEFAULT '{}',                -- 설정 7개. 없는 키는 코드 기본값 (API 29 · 30)
    CHECK ((email IS NULL) = (password IS NULL))                        -- 둘 다 있거나 둘 다 없거나
);

-- 가입한 기기 (친구 초대 부정 방지: 같은 기기 반복 가입이면 초대 보상 없음). 탈퇴해도 남김
CREATE TABLE signup_devices (
    device_hash text        NOT NULL,             -- 프론트가 만든 기기 값 (64자 이하)
    user_id     bigint      NOT NULL REFERENCES users (id),
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (device_hash, user_id)
);
CREATE INDEX idx_signup_devices_user ON signup_devices (user_id);

-- 친구 초대 (2026-11-23 8단계, api.md 35 · 36). 받은 사람(invitee)당 한 행. 탈퇴해도 남김
CREATE TABLE referrals (
    id                      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inviter_id              bigint      NOT NULL REFERENCES users (id),
    invitee_id              bigint      NOT NULL UNIQUE REFERENCES users (id),  -- 코드는 한 번만
    device_hash             text        NOT NULL,                              -- 입력할 때 기기 값
    entered_at              timestamptz NOT NULL DEFAULT now(),
    qualified_at            timestamptz,                                       -- 입력 뒤 첫 연결이 끝난 시각
    qualified_connection_id bigint,                                            -- 그 연결 (다른 모듈 테이블이라 FK 아님, 23 referralReward)
    inviter_rewarded        boolean     NOT NULL DEFAULT false,                -- 초대한 사람 1,000 (10명까지)
    invitee_rewarded        boolean     NOT NULL DEFAULT false,                -- 받은 사람 1,000
    CHECK (inviter_id <> invitee_id),
    CHECK ((qualified_at IS NULL) = (qualified_connection_id IS NULL)),
    CHECK (qualified_at IS NOT NULL OR NOT (inviter_rewarded OR invitee_rewarded))
);
CREATE INDEX idx_referrals_inviter ON referrals (inviter_id, id);

-- 베타 피드백 (2026-11-23, api.md 42). RATING은 사용자당 하루(오전 5시 기준) 1번. 탈퇴해도 남김
CREATE TABLE beta_feedback (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       bigint      NOT NULL REFERENCES users (id),
    kind          text        NOT NULL CHECK (kind IN ('RATING', 'BUG', 'SUGGESTION')),
    score         int         CHECK (score BETWEEN 1 AND 4),
    text          text        CHECK (char_length(text) <= 1000),
    connection_id bigint,                                 -- 다른 모듈 테이블이라 FK 아님
    feedback_day  date        NOT NULL,                   -- 오전 5시 기준 한국 날짜
    created_at    timestamptz NOT NULL DEFAULT now(),
    CHECK ((kind = 'RATING') = (score IS NOT NULL)),
    CHECK (kind = 'RATING' OR text IS NOT NULL)
);
CREATE UNIQUE INDEX uq_beta_feedback_rating ON beta_feedback (user_id, feedback_day) WHERE kind = 'RATING';

-- 알림 (2026-11-24, api.md 43 · 44, ADR 0035). 다른 모듈의 일(단계 상승 · 출석 · 초대 보상)을 user가 받아 적는다. 탈퇴하면 지움
CREATE TABLE notifications (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     bigint      NOT NULL REFERENCES users (id),
    type        text        NOT NULL CHECK (type IN ('STAGE_UP', 'ATTENDANCE', 'REFERRAL_REWARD')),
    title       text        NOT NULL,
    body        text,
    link        text,                                      -- 프론트 경로 (예: /mates/me)
    dedup_key   text,                                      -- 같은 일을 두 번 적지 않게 (예: 출석 날짜, 연결 id)
    created_at  timestamptz NOT NULL DEFAULT now(),
    read_at     timestamptz
);
CREATE INDEX idx_notifications_user ON notifications (user_id, id DESC);
CREATE UNIQUE INDEX uq_notifications_dedup ON notifications (user_id, type, dedup_key) WHERE dedup_key IS NOT NULL;

-- 출시 알림 신청 (2026-11-24, api.md 45). 정원이 찬 뒤 로그인 없이 이메일만 받음
CREATE TABLE beta_waitlist (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email       text        NOT NULL UNIQUE,               -- 소문자로 저장
    created_at  timestamptz NOT NULL DEFAULT now()
);

-- 준비 중 메이트 공개 알림 신청 (2026-11-24, api.md 46). 메이트는 다른 모듈 테이블이라 FK 아님
CREATE TABLE mate_alerts (
    user_id      bigint      NOT NULL REFERENCES users (id),
    character_id bigint      NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, character_id)
);

-- 크레딧 지급 건 (2026-11-23, ADR 0031). 잔액 = 소멸 전 지급 건의 remaining 합. 탈퇴해도 남김
--   ATTENDANCE: 하루 1,500 (granted_on = 오전 5시 기준 그날), expires_at = 다음 오전 5시
--   INVITE: 친구 초대 보상 1,000, 사라지지 않음 (source = 초대 id, 같은 보상은 한 번만)
CREATE TABLE credit_grants (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     bigint      NOT NULL REFERENCES users (id),
    kind        text        NOT NULL CHECK (kind IN ('ATTENDANCE', 'INVITE')),
    amount      int         NOT NULL CHECK (amount > 0),
    remaining   int         NOT NULL,
    granted_on  date,                                   -- ATTENDANCE만
    source      text,                                   -- INVITE만 (중복 지급 방지)
    expires_at  timestamptz,                            -- ATTENDANCE만
    expired_at  timestamptz,                            -- 소멸 처리한 시각 (남은 양은 그대로 두고 표시)
    created_at  timestamptz NOT NULL DEFAULT now(),
    CHECK (remaining BETWEEN 0 AND amount),
    CHECK ((kind = 'ATTENDANCE') = (granted_on IS NOT NULL AND expires_at IS NOT NULL)),
    CHECK ((kind = 'INVITE') = (source IS NOT NULL))
);
CREATE UNIQUE INDEX uq_credit_grants_attendance ON credit_grants (user_id, granted_on) WHERE kind = 'ATTENDANCE';
CREATE UNIQUE INDEX uq_credit_grants_invite ON credit_grants (user_id, source) WHERE kind = 'INVITE';

-- 크레딧 내역 (34). 바뀔 때마다 한 행, balance_after = 그 뒤 전체 잔액
CREATE TABLE credit_ledger (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       bigint      NOT NULL REFERENCES users (id),
    type          text        NOT NULL CHECK (type IN ('ATTENDANCE', 'INVITE', 'USE', 'EXPIRE')),
    title         text        NOT NULL,
    change        int         NOT NULL CHECK (change <> 0),
    balance_after int         NOT NULL CHECK (balance_after >= 0),
    request_id    uuid,                                 -- USE면 요청 id (같은 요청은 한 번만 차감)
    created_at    timestamptz NOT NULL DEFAULT now(),
    CHECK ((type = 'USE') = (request_id IS NOT NULL)),
    CHECK ((change > 0) = (type IN ('ATTENDANCE', 'INVITE')))
);
CREATE INDEX idx_credit_ledger_user ON credit_ledger (user_id, id);
CREATE UNIQUE INDEX uq_credit_ledger_request ON credit_ledger (user_id, request_id) WHERE request_id IS NOT NULL;

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
    created_at      timestamptz  NOT NULL DEFAULT now(),
    format          text         NOT NULL DEFAULT 'ALIVING2D_V2' CHECK (format IN ('CUBISM', 'ALIVING2D_V2')),  -- 프론트 렌더러 (api.md 9 · 31). 기본 Aliving2D (2026-11-24)
    -- Aliving2D 런타임 (2026-11-25, api.md 9 · 16 · 31 · 48): 버킷 루트 기준 경로. 여러 모델이 같은 엔진을 쓴다. CUBISM이면 NULL
    engine_path     text,                                 -- 예: aliving2d/v4/engine.js
    player_path     text,
    reactions_path  text
);

CREATE TABLE live2d_motions (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    model_id   bigint NOT NULL REFERENCES live2d_models (id) ON DELETE CASCADE,
    group_name text   NOT NULL,                  -- model3.json의 Motions 그룹 (Idle, TapBody ...)
    name       text   NOT NULL,                  -- 사람이 알아볼 이름 (예: 손 흔들기)
    label      text,                             -- 한국어 이름 (Aliving2D는 name이 영문 이름, 2026-11-23)
    file_path  text   NOT NULL,                  -- .motion3.json 상대 경로
    UNIQUE (model_id, file_path),
    UNIQUE (id, model_id)                        -- 반응 매핑의 복합 FK용
);

CREATE TABLE live2d_expressions (
    id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    model_id  bigint NOT NULL REFERENCES live2d_models (id) ON DELETE CASCADE,
    name      text   NOT NULL,                   -- 예: 삐짐, 웃음
    label     text,                              -- 한국어 이름 (Aliving2D는 name이 영문 이름, 2026-11-23)
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
-- 5. 캐릭터(메이트) (S03, S04)
-- ---------------------------------------------------------------------
-- 2026-11-23: 스터디룸 · 배경(study_rooms · room_backgrounds · user_unlocked_backgrounds)을 지움. 메이트 = 캐릭터 1명
-- 모델은 하나. 준비 중 메이트는 모델이 없을 수 있음 (model_id NULL)
CREATE TABLE characters (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    model_id          bigint      UNIQUE REFERENCES live2d_models (id),  -- 준비 중 메이트(coming_soon)는 NULL 가능
    name              text        NOT NULL,
    intro             text,
    profile_image_url text,                                -- CDN 상대 경로 또는 전체 URL (avatars.thumbnail_url과 같은 규칙)
    home_image_url    text,                                -- 홈 포스터 카드에 겹치는 캐릭터 전신(투명 배경). 홈(api.md 11)의 profileImageUrl로 나감, 없으면 profile_image_url (2026-11-06)
    sort_order        int         NOT NULL DEFAULT 0,      -- 홈 메이트 리스트 표시 순서
    is_active         boolean     NOT NULL DEFAULT true,
    created_at        timestamptz NOT NULL DEFAULT now(),
    -- 메이트 화면 칸 (12/1 베타, api.md 31). 이미지 · 소리는 CDN 상대 경로 또는 전체 URL
    one_liner         text,                                -- 한 줄 소개 (30자 안팎)
    hashtags          text[]      NOT NULL DEFAULT '{}',   -- 해시태그, 순서대로 표시 (옛 personality를 대체)
    age_rating        text        NOT NULL DEFAULT 'ALL' CHECK (age_rating IN ('ALL', 'ADULT')),
    world             text,                                -- 캐릭터 · 세계관 설명 (1,000자 안팎)
    my_role           text,                                -- 내 역할
    illustration_url  text,                                -- 세로 일러스트 (메이트 상세)
    card_image_url    text,                                -- 홈 메이트 카드
    voice_sample_url  text,                                -- '목소리 듣기' mp3 (아래 목소리로 만든 샘플)
    -- 메이트 목소리 (2026-11-25, ADR 0036): 대화 음성(api.md 28)이 이 값으로 TTS. NULL이면 운영 설정 기본값
    tts_voice         text,                                -- 예: coral
    tts_model         text,                                -- 예: gpt-4o-mini-tts
    tts_instructions  text,                                -- 말투 지시 (instructions를 받는 모델만)
    stage_names       text[]      NOT NULL DEFAULT '{}',   -- 친밀도 단계 이름 5개 (친밀도 단계에서 씀)
    coming_soon       boolean     NOT NULL DEFAULT false,  -- 준비 중 메이트 (조회는 되고 연결은 막음)
    CHECK (coming_soon OR model_id IS NOT NULL)            -- 연결할 수 있는 메이트는 모델이 있어야 함
);
CREATE INDEX idx_characters_sort ON characters (sort_order);

-- 메이트 배경 (2026-11-23, ADR 0032): 연결 화면 뒤 배경. 대화 흐름에 따라 결정 모델이 이 목록에서 고른다 (API 20 · 28)
-- 옛 room_backgrounds(룸 기준)를 메이트 기준으로 다시 둔 것. 실제 배경 목록 · 이미지 · guide 문구는 확정 필요
CREATE TABLE character_backgrounds (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id  bigint  NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    code          text    NOT NULL,                     -- 영문 이름 (예: classroom_day). API · 결정 모델 보기 값
    name          text    NOT NULL,                     -- 표시 이름 (예: 낮 교실)
    image_url     text    NOT NULL,                     -- CDN 상대 경로 또는 전체 URL (ADR 0024)
    guide         text,                                 -- 언제 이 배경인지 (결정 모델 보기 설명). 없으면 name만 보기로
    is_default    boolean NOT NULL DEFAULT false,       -- 연결을 시작할 때 배경
    sort_order    int     NOT NULL DEFAULT 0,
    is_active     boolean NOT NULL DEFAULT true,
    UNIQUE (character_id, code)
);
CREATE UNIQUE INDEX uq_character_backgrounds_default
    ON character_backgrounds (character_id) WHERE is_default;  -- 메이트당 기본 배경 하나

-- ---------------------------------------------------------------------
-- 6. 캐릭터 콘텐츠 / 관계 / 해금 / 기억 (S04, S09, S11)
-- ---------------------------------------------------------------------
-- 친밀도 해금 콘텐츠 (2026-11-23 5단계): 단계가 오르면 새 표정 · 보이스 대사가 열림 (PRD 8장)
CREATE TABLE character_contents (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id    bigint  NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    type            text    NOT NULL CHECK (type IN ('EXPRESSION', 'VOICE_LINE')),
    title           text    NOT NULL,
    body            text,                                   -- 보이스 대사 문구
    asset_url       text,                                   -- 미리 녹음한 음성 파일 (CDN 상대 경로 또는 전체 URL)
    expression_name text,                                   -- EXPRESSION이면 모델의 표정 이름
    unlock_stage    int     NOT NULL CHECK (unlock_stage BETWEEN 2 AND 5),  -- 이 친밀도 단계가 되면 열림
    is_active       boolean NOT NULL DEFAULT true,
    CHECK ((type = 'EXPRESSION') = (expression_name IS NOT NULL))
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
    last_severe_at        timestamptz,                       -- 심한 말(HATE_OR_SEXUAL)을 받은 마지막 시각. 그 한국 날짜에는 대화로 오르지 않음
    first_met_at          timestamptz NOT NULL DEFAULT now(),
    last_met_at           timestamptz NOT NULL DEFAULT now(),
    -- 친밀도 (2026-11-23 5단계): 인정된 함께한 시간 누적으로 오르고 내려가지 않음. 호감도(relation_points)는 대화용으로 따로 남음
    stage                 int         NOT NULL DEFAULT 1 CHECK (stage BETWEEN 1 AND 5),
    credited_seconds      int         NOT NULL DEFAULT 0 CHECK (credited_seconds >= 0),  -- 인정된 시간 누적 (하루 2시간 한도)
    total_connected_seconds int       NOT NULL DEFAULT 0 CHECK (total_connected_seconds >= 0),  -- 연결 시간 누적 (내 메이트 화면)
    UNIQUE (user_id, character_id)
);

-- 하루(오전 5시 기준) 인정된 시간. 하루 한도 2시간을 지키려고
CREATE TABLE relation_daily (
    relation_id      bigint NOT NULL REFERENCES user_character_relations (id),
    day              date   NOT NULL,
    credited_seconds int    NOT NULL DEFAULT 0 CHECK (credited_seconds BETWEEN 0 AND 7200),
    chat_turns       int    NOT NULL DEFAULT 0 CHECK (chat_turns >= 0),  -- 그날 이 메이트와 대화 턴 수 (0이면 인정 절반)
    PRIMARY KEY (relation_id, day)
);
CREATE INDEX idx_relations_character ON user_character_relations (character_id);

CREATE TABLE user_unlocked_contents (
    user_id     bigint      NOT NULL REFERENCES users (id),
    content_id  bigint      NOT NULL REFERENCES character_contents (id) ON DELETE CASCADE,
    unlocked_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, content_id)
);

-- 시험, 목표, 약속 (캐릭터 공통). 사용자가 직접 말한 내용이라 탈퇴 시 서버가 삭제
-- 2026-11-24: 저장 · 조회 API(13 · 14) 삭제. 남은 행은 대화 프롬프트가 계속 읽음
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
-- 7. 연결 (S05, S06, S09, S10)
-- ---------------------------------------------------------------------
-- 2026-11-23: study_sessions → connections, session_events → connection_events, session_characters 삭제(character_id 칸으로)
-- 공부 시간 = focus_seconds + distract_seconds (쉬는 시간 제외). 시간 칸은 다음 단계(연결 새 기능)에서 바뀜
-- 연결은 사용자가 끊을 때만 끝남. 탭이 닫히면 heartbeat 기준으로 자동 마감
CREATE TABLE connections (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           bigint      NOT NULL REFERENCES users (id),
    character_id      bigint      NOT NULL REFERENCES characters (id),  -- 연결한 메이트
    started_at        timestamptz NOT NULL DEFAULT now(),
    ended_at          timestamptz,                        -- NULL이면 진행 중
    end_type          text        CHECK (end_type IN ('NORMAL', 'AUTO_CLOSED', 'AUTO_AWAY', 'MINOR_LIMIT')),
    last_heartbeat_at timestamptz,                        -- 1분에 한 번 정도만 갱신
    -- 마지막 heartbeat 때 프론트가 보낸 누적값 (API 21). 자동 마감 · 이어하기 · 청소년 남은 시간에 사용
    heartbeat_connected_seconds int,
    heartbeat_present_seconds   int,
    heartbeat_camera_on_seconds int,
    heartbeat_pip_seconds       int,
    -- 아래는 끊을 때 저장 (API 22). 정상 끊기면 프론트 값, 자동 마감이면 마지막 heartbeat 값(없으면 0)
    connected_seconds int         NOT NULL DEFAULT 0 CHECK (connected_seconds >= 0),  -- 연결 시간
    present_seconds   int         NOT NULL DEFAULT 0 CHECK (present_seconds >= 0),    -- 함께한 시간 (카메라 + 얼굴)
    away_seconds      int         NOT NULL DEFAULT 0 CHECK (away_seconds >= 0),       -- 자리 비움 (이벤트로 계산)
    camera_on_seconds int         NOT NULL DEFAULT 0 CHECK (camera_on_seconds >= 0),  -- 카메라 켠 시간 (지표)
    pip_seconds       int         NOT NULL DEFAULT 0 CHECK (pip_seconds >= 0),        -- PIP 시간 (지표)
    message_count     int         NOT NULL DEFAULT 0 CHECK (message_count >= 0),      -- 답장까지 받은 대화 턴 수 (답장 저장 때 +1)
    return_count      int         NOT NULL DEFAULT 0,
    mood              text,                               -- 그날의 분위기 (연결마다 새로, 확정 필요)
    closing_line      text,                               -- 완료 대사 (종료 때 저장, 연결 기록 공용)
    background_code   text,                               -- 지금 배경 character_backgrounds.code (NULL이면 메이트 기본 배경, 2026-11-23)
    notified_stage    int,                                -- 연결 중 단계 상승을 알린 마지막 단계 (api.md 21 stageUp, NULL이면 시작 단계, 2026-11-24)
    CHECK (ended_at IS NULL OR ended_at >= started_at),
    CHECK ((ended_at IS NULL) = (end_type IS NULL)),
    CHECK (present_seconds <= camera_on_seconds AND camera_on_seconds <= connected_seconds AND pip_seconds <= connected_seconds)
);
CREATE INDEX idx_connections_user_started ON connections (user_id, started_at DESC);
CREATE INDEX idx_connections_character ON connections (character_id);
CREATE UNIQUE INDEX uq_connections_one_in_progress
    ON connections (user_id) WHERE ended_at IS NULL;      -- 사용자당 진행 중 연결 하나
CREATE INDEX idx_connections_in_progress
    ON connections (last_heartbeat_at) WHERE ended_at IS NULL;  -- 자동 마감 배치용

-- 2026-11-23 베타 이벤트 4종 (API 18): PRESENT 얼굴 감지 시작 / AWAY 자리 비움 / RETURN 복귀 / IDLE_TALK 말 걸기 타이머
--   옛 FOCUS · BREAK · 동작 이벤트(DROWSY · YAWN · WAVE · HEAD_TURN)는 지난 기록이라 CHECK에만 남기고 API는 받지 않음
CREATE TABLE connection_events (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    connection_id  bigint      NOT NULL REFERENCES connections (id) ON DELETE CASCADE,
    type           text        NOT NULL CHECK (type IN ('PRESENT', 'AWAY', 'RETURN', 'IDLE_TALK',
                                   'FOCUS', 'BREAK', 'DROWSY', 'YAWN', 'WAVE', 'HEAD_TURN')),
    false_positive boolean     NOT NULL DEFAULT false,   -- '아니야' 버튼 (오탐이면 집중으로 계산)
    occurred_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_connection_events_connection ON connection_events (connection_id, occurred_at);

-- 호감도 변화 이력 (계속 남음). delta는 실제로 바뀐 만큼(0~100 밖으로 나간 몫은 잘림) → 50 + 기록의 합 = 지금 점수
--   CHAT: 결정 모델이 고른 말의 종류(signal) → 캐릭터 점수표 → 오르는 쪽만 하루 상한. 심한 말은 지금 값의 %로 깎음
--   SESSION_EVENT: 자리 비움을 끝낸 상태 이벤트에서, 1분 넘게 비웠으면 감점 (하루 상한). 1:3 룸이면 캐릭터마다 한 행씩
--   STREAK: 연속 출석 (2일째부터 하루 한 번, 세션 첫 FOCUS)
--   EVENT: 기간 한정 이벤트(character_contents의 EVENT)로 받은 점수
--   STUDY_TIME · FIRST_MEET · FOCUS_BONUS: 더 기록하지 않음 (호감도 최종안. 공부 시간은 레벨에만)
--   ADJUST: 손으로 맞춘 점수 (local 흐름 테스트의 '호감도를 N으로'). 지금 점수와 기록의 합이 계속 맞게 맞춘 만큼을 남김
CREATE TABLE relation_logs (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    relation_id      bigint      NOT NULL REFERENCES user_character_relations (id),
    connection_id    bigint      REFERENCES connections (id),
    connection_event_id bigint   REFERENCES connection_events (id),  -- SESSION_EVENT일 때 원인 이벤트 (2026-11-23 이름 변경)
    request_id       uuid,                                      -- CHAT일 때 메시지 전송 요청 id (메시지는 지워지므로 FK 아님)
    signal           text,                                      -- CHAT일 때 말의 종류 (COMPLIMENT, HATE_OR_SEXUAL 등). 그날 오름 멈춤 · 문의 대응
    delta            int         NOT NULL,                      -- 음수 가능
    reason           text        NOT NULL CHECK (reason IN (
                         'STUDY_TIME', 'STREAK', 'FOCUS_BONUS', 'FIRST_MEET', 'EVENT', 'CHAT', 'SESSION_EVENT', 'ADJUST')),
    created_at       timestamptz NOT NULL DEFAULT now(),
    CHECK ((reason = 'CHAT') = (request_id IS NOT NULL)),
    CHECK ((reason = 'SESSION_EVENT') = (connection_event_id IS NOT NULL)),
    CHECK (delta <> 0),                                        -- 0점이면 기록하지 않음
    CONSTRAINT relation_logs_chat_range
        CHECK (reason <> 'CHAT' OR delta BETWEEN -100 AND 5)   -- 대화 한 번: 오르는 쪽은 작게, 비율 감점은 크게
);
CREATE INDEX idx_relation_logs_relation ON relation_logs (relation_id, created_at DESC);
CREATE UNIQUE INDEX uq_relation_logs_request
    ON relation_logs (relation_id, request_id) WHERE request_id IS NOT NULL;              -- 같은 요청은 한 번만 반영
CREATE UNIQUE INDEX uq_relation_logs_session_event
    ON relation_logs (relation_id, connection_event_id) WHERE connection_event_id IS NOT NULL;  -- 같은 이벤트는 캐릭터마다 한 번만

-- 공부 종료 정산 때의 레벨 (세션마다 한 번). 리포트를 다시 볼 때 같은 값을 주려고 남긴다 (레벨은 점수 기록으로 다시 계산할 수 없음)
CREATE TABLE relation_settlements (
    relation_id     bigint      NOT NULL REFERENCES user_character_relations (id),
    connection_id   bigint      NOT NULL REFERENCES connections (id),
    present_seconds int         NOT NULL CHECK (present_seconds >= 0),
    level_before    int         NOT NULL,
    level_after     int         NOT NULL,
    credited_seconds int        NOT NULL DEFAULT 0,     -- 이 연결에서 인정된 친밀도 시간 (2026-11-23)
    stage_before    int         NOT NULL DEFAULT 1,     -- 친밀도 단계 전후
    stage_after     int         NOT NULL DEFAULT 1,
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (relation_id, connection_id)
);

-- 운영 설정 (ADR 0026): 호감도 숫자(capsule.relation.*) 중 실험실 '운영 설정'에서 바꾼 칸만. 없는 칸은 application.yml 값
CREATE TABLE relation_settings (
    section    text        PRIMARY KEY CHECK (section IN ('relation')),
    overrides  jsonb       NOT NULL DEFAULT '{}',  -- 칸 → 값 (예: {"chatDailyGainCap": 20}). 칸 이름은 설정 레코드의 필드 이름
    updated_at timestamptz NOT NULL DEFAULT now()
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
    connection_id   bigint      NOT NULL,                 -- 다른 모듈(session) 테이블이라 FK 아님
    content         text        NOT NULL CHECK (char_length(content) BETWEEN 1 AND 200),
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (conversation_id, connection_id)
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

-- 대화방 원문 (2026-11-23 6단계: 지우지 않음, 32 대화방 기록). 연결이 끝나면 카드에 넣고 recapped_at만 채운다
-- (카드 쓰기에 실패하면 비워 둬서 다음 종료 때 같이 넣음). 답장 프롬프트는 지금처럼 이번 연결 메시지만 씀
-- 음성(TTS)은 저장하지 않고 응답으로 바로 전달
CREATE TABLE chat_messages (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id bigint      NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    connection_id   bigint      NOT NULL REFERENCES connections (id),
    request_id      uuid        NOT NULL,                 -- 사용자 메시지와 그 답변이 같은 값
    sender          text        NOT NULL CHECK (sender IN ('USER', 'CHARACTER')),
    content         text        NOT NULL,
    signal          text,                                 -- USER일 때 말의 종류 (같은 세션 안 반복 규칙: 고민 1번, 무례 반복, 칭찬 연타)
    created_at      timestamptz NOT NULL DEFAULT now(),
    recapped_at     timestamptz,                          -- 흐름 카드에 넣은 시각. null이면 아직 (2026-11-23)
    input_type      text CHECK (input_type IN ('TEXT', 'QUICK', 'VOICE')),  -- USER만 (2026-11-23 전 행은 null)
    mode            text CHECK (mode IN ('BASIC', 'ADVANCED')),             -- CHARACTER만: 이 답장의 대화 모드
    voice_charged   boolean NOT NULL DEFAULT false,      -- CHARACTER만: 이 답장에서 보이스 20을 깎았는지 (api.md 28 '음성', 재전송 charged 계산용, 2026-11-24)
    rating          text CHECK (rating IN ('UP', 'DOWN')),                  -- CHARACTER만: 사용자 평가 (api.md 40)
    hidden_at       timestamptz,                          -- 신고로 가린 시각 (api.md 41). 대화방 · 이전 대화에서 빠짐
    UNIQUE (conversation_id, request_id, sender)          -- 재전송돼도 질문·답변 각각 한 번만
);
CREATE INDEX idx_chat_messages_connection ON chat_messages (connection_id, id);
CREATE INDEX idx_chat_messages_room ON chat_messages (conversation_id, id);  -- 32 대화방 기록 (최신순 커서)

-- 답변 신고 (2026-11-23, api.md 41). 신고한 사람 · 답장마다 한 번. 처리는 베타 동안 운영자가 DB에서
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

-- 안전 위반 기록 (2026-11-23, api.md 20 '안전'). 24시간 안 3번이면 24시간 대화 정지 (횟수 · 기간 확정 필요). 탈퇴해도 남김
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

-- LLM 호출 기록 (지우지 않고 계속 쌓임, 비용·크레딧 근거 + 품질·지연 분석). 호출 한 번 = 한 행, 실패도 기록
-- 실험실·평가 호출은 사용자·세션이 없다 (source LAB · EVAL)
CREATE TABLE llm_usages (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id            bigint         REFERENCES users (id),
    connection_id      bigint         REFERENCES connections (id),
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
    expression_guide     jsonb       NOT NULL DEFAULT '{}',  -- 표정 → 언제 쓰는지 (예: {"수줍음": "칭찬 · 다정한 말에"}). 대사를 다 만든 뒤 결정 모델이 이 설명을 보고 expressions 중 하나를 고른다
    stage_tones          jsonb       NOT NULL DEFAULT '{}',  -- 관계 단계 → 그 단계의 관계 설명 (거리감 · 호칭 · 챙김 수위, ADR 0022 결정 19)
    study_style          text        NOT NULL,           -- 공부 중 답 길이 · 구성 (예: 1~2문장, 공부 얘기 중심)
    break_style          text        NOT NULL,           -- 쉬는 시간 답 길이 · 구성 (예: 2~4문장, 공부 재촉 없이 수다)
    samples              jsonb       NOT NULL DEFAULT '[]',  -- 관계 단계별 말투 샘플 [{"minStage", "user", "reply"}] (ADR 0022 결정 19)
    lore                 jsonb       NOT NULL DEFAULT '[]',  -- 로어북 [{"title", "keys": [...], "text"}]. 사용자 말에 keys 중 하나가 있으면 그 카드만 답장 프롬프트에 (최대 2장)
    content_version      integer     NOT NULL DEFAULT 0,     -- 불러온 profile.yml의 version. 파일 쪽이 더 높으면 앱 시작 때 파일로 다시 불러온다 (lab_edited_at이 있으면 안 덮음)
    lab_edited_at        timestamptz,                        -- 실험실에서 마지막으로 고친 때. NULL이면 파일 값 그대로. 파일 값으로 바꾸면(reseed) NULL
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

-- 운영 설정 (ADR 0026): 대화 설정(capsule.dialogue.*)과 AI 공급자 · 모델(capsule.llm.*) 중 실험실 '운영 설정'에서 바꾼 칸만.
-- 없는 칸은 application.yml 값. API 키 · 공급자 주소 · 원문 기록 여부는 바꿀 수 있는 칸이 아니다(환경변수 · yml로만)
CREATE TABLE dialogue_settings (
    section    text        PRIMARY KEY CHECK (section IN ('dialogue', 'llm')),
    overrides  jsonb       NOT NULL DEFAULT '{}',  -- 칸 → 값 (예: {"provider": "anthropic", "anthropic.models": {...}}). 점은 안쪽 칸
    updated_at timestamptz NOT NULL DEFAULT now()
);

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
