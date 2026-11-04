-- 모듈: dialogue (담당: seunghee)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 세계관 · 캐릭터 설정을 데이터로 (ADR 0022 결정 15). 캐릭터를 더해도 코드는 바뀌지 않는다
-- characters(content 모듈)는 화면용 정보만 두고, 대사를 만드는 데 쓰는 설정은 여기 둔다
-- 처음에는 classpath:characters/<code>/의 YAML을 한 번 불러와 채운다(CharacterContentSeeder). 그 뒤에는 DB가 원본

-- 세계: 여러 캐릭터가 같은 세계를 쓸 수 있다
CREATE TABLE worlds (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       text        NOT NULL UNIQUE,              -- 예: hogwarts
    name       text        NOT NULL,
    setting    text        NOT NULL,                     -- 무대 설명 (프롬프트의 세계 블록)
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- 세계 지식 카드: 사용자 말 · 최근 대화에 키워드가 나오면 그 카드만 꺼낸다 (한 턴 최대 4장)
CREATE TABLE world_lore (
    id       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    world_id bigint NOT NULL REFERENCES worlds (id) ON DELETE CASCADE,
    code     text   NOT NULL,
    title    text   NOT NULL,
    keywords text[] NOT NULL DEFAULT '{}',
    body     text   NOT NULL,
    UNIQUE (world_id, code)
);

-- 세계 사건: 가끔 저절로 일어나 캐릭터가 반응할 거리 (예: 사서가 지나가며 노려봄)
CREATE TABLE world_events (
    id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    world_id  bigint NOT NULL REFERENCES worlds (id) ON DELETE CASCADE,
    code      text   NOT NULL,
    text      text   NOT NULL,
    hook      text,                                      -- 어울리는 반응
    lore_code text,                                      -- 같이 꺼낼 지식 카드
    weight    int    NOT NULL DEFAULT 1 CHECK (weight > 0),
    UNIQUE (world_id, code)
);

-- 캐릭터 설정 (캐릭터당 하나)
CREATE TABLE character_profiles (
    character_id         bigint      PRIMARY KEY REFERENCES characters (id),
    world_id             bigint      NOT NULL REFERENCES worlds (id),
    code                 text        NOT NULL UNIQUE,    -- 예: malfoy (콘텐츠 폴더 이름)
    speaker              text        NOT NULL,           -- 대사에서 부르는 이름
    bible                text        NOT NULL,           -- 정체성 · 화법 · 대화 원칙 · 형식
    rules                text        NOT NULL,           -- 절대 규칙 (연출보다 우선)
    safety               text        NOT NULL CHECK (safety IN ('MINOR', 'ADULT')),  -- 미성년 설정이면 연애 · 성적 표현 금지
    expressions          text[]      NOT NULL,           -- 고를 수 있는 표정 (답장 emotion 값)
    expression_fallbacks jsonb       NOT NULL DEFAULT '{}',  -- 에셋이 없는 표정 → 대신 쓸 표정 (예: {"화남": "찡그림"})
    stage_tones          jsonb       NOT NULL DEFAULT '{}',  -- 관계 단계 → 말투 지시 한 줄 (예: {"1": "아직 서먹함"})
    study_style          text        NOT NULL,           -- 공부 중일 때 말하는 방식 (답 길이 · 구성. PM: 1~2문장)
    break_style          text        NOT NULL,           -- 쉬는 시간일 때 말하는 방식 (PM: 2~4문장)
    updated_at           timestamptz NOT NULL DEFAULT now()
);

-- 대화 기술: 한 턴에 하나를 골라 예시 2개와 함께 장면 카드에 넣는다
CREATE TABLE character_moves (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id bigint NOT NULL REFERENCES character_profiles (character_id) ON DELETE CASCADE,
    code         text   NOT NULL,
    name         text   NOT NULL,
    how          text   NOT NULL,
    signals      text[] NOT NULL DEFAULT '{}',           -- 어울리는 사용자 말의 종류 (SignalType). 비면 평소에
    modes        text[] NOT NULL DEFAULT '{}',           -- 쓰는 때: STUDY(공부 중) · BREAK(쉬는 시간). 비면 언제나
    examples     jsonb  NOT NULL DEFAULT '[]',           -- [{"user": "…", "reply": "…"}]
    UNIQUE (character_id, code)
);

-- 상황별 미리 쓴 대사: 같은 상황이면 sort 순서대로 돌려 쓴다 (PM 2026-11-04 확정)
--   situation: GREETING · AWAY · AWAY_2 · AWAY_3 · RETURN · BREAK · BREAK_END · DROWSY · YAWN · WAVE · HEAD_TURN
--              · REPLY_FALLBACK(5초 넘김) · CLOSING(완료 대사)
CREATE TABLE character_lines (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id bigint NOT NULL REFERENCES character_profiles (character_id) ON DELETE CASCADE,
    situation    text   NOT NULL,
    min_stage    int    NOT NULL DEFAULT 1 CHECK (min_stage >= 1),  -- 이 관계 단계부터 쓰는 대사
    sort         int    NOT NULL DEFAULT 0,
    text         text   NOT NULL,                        -- {name}은 닉네임으로 바뀜
    expression   text                                    -- 이 대사의 표정 (없으면 표정 없음)
);
CREATE INDEX idx_character_lines_situation ON character_lines (character_id, situation, sort);

-- 대화 점수표 (말의 종류별). 없으면 설정 capsule.dialogue.chat-scores
CREATE TABLE character_scores (
    character_id     bigint  NOT NULL REFERENCES character_profiles (character_id) ON DELETE CASCADE,
    signal           text    NOT NULL,                   -- SignalType
    points           int     NOT NULL,
    loss_percent     int     NOT NULL DEFAULT 0 CHECK (loss_percent BETWEEN 0 AND 100),
    once_per_session boolean NOT NULL DEFAULT false,
    repeat_points    int,
    PRIMARY KEY (character_id, signal)
);

-- 피할 말버릇 (예: "흥"은 첫마디로 쓰지 않기, "아버지"는 세션에 한 번)
CREATE TABLE character_habits (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id bigint NOT NULL REFERENCES character_profiles (character_id) ON DELETE CASCADE,
    phrase       text   NOT NULL,
    rule         text   NOT NULL CHECK (rule IN ('NO_OPENING', 'ONCE_PER_SESSION')),
    UNIQUE (character_id, phrase)
);
