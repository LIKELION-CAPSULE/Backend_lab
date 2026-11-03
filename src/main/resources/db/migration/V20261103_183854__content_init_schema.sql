-- 모듈: content (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 출처: docs/specs/schema.sql 섹션 3·4·5·6 (2026-11-03 노션 스냅샷). user_unlocked_backgrounds는 relation 모듈.

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
    trigger_type  text     NOT NULL,   -- 허용 값은 서버 enum으로 관리 (GREETING, FOCUS, GAZE_AWAY, AWAY, RETURN, BREAK, LONG_FOCUS ...)
    intensity     smallint CHECK (intensity BETWEEN 1 AND 3),   -- 눈치 / 말 걸기 / 서운함
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
    thumbnail_url text        NOT NULL,                  -- 선택 화면 카드
    sort_order    int         NOT NULL DEFAULT 0,
    is_active     boolean     NOT NULL DEFAULT true,     -- 고른 사용자가 있으니 지우지 말고 비활성화
    created_at    timestamptz NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 5. 스터디룸 / 배경 / 캐릭터 (S03, S04)
-- ---------------------------------------------------------------------
-- 1:1 / 1:3 구분은 저장하지 않음: 룸에 속한 캐릭터 수로 계산
CREATE TABLE study_rooms (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          text        NOT NULL,
    description   text,
    thumbnail_url text,
    sort_order    int         NOT NULL DEFAULT 0,
    is_active     boolean     NOT NULL DEFAULT true,
    created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE room_backgrounds (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_id      bigint  NOT NULL REFERENCES study_rooms (id) ON DELETE CASCADE,
    name         text    NOT NULL,
    image_url    text    NOT NULL,
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
    personality       text[]      NOT NULL DEFAULT '{}',   -- 성격 태그 (예: {츤데레, 같은 반 친구}), 순서대로 표시
    intro             text,
    profile_image_url text,
    sort_order        int         NOT NULL DEFAULT 0,      -- 룸 안 표시 순서
    is_active         boolean     NOT NULL DEFAULT true,
    created_at        timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id, room_id)                                   -- session_characters 복합 FK용
);
CREATE INDEX idx_characters_room ON characters (room_id, sort_order);

-- ---------------------------------------------------------------------
-- 6. (일부) 캐릭터 콘텐츠
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
