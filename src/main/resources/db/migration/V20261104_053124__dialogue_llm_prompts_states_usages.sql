-- 모듈: dialogue (담당: seunghee)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- LLM 파이프라인 (ADR 0021): 프롬프트 DB 버전 관리, 대화 엔진 상태, llm_usages를 호출 기록으로 넓힘

-- 프롬프트 버전. 고칠 때는 본문을 바꾸지 않고 새 버전(DRAFT)을 만든다 → 실험실에서 확인 → ACTIVE.
-- (키, 캐릭터)마다 ACTIVE는 하나. 새 버전을 켜면 이전 ACTIVE는 ARCHIVED (되돌리기 = 예전 버전을 다시 켬)
CREATE TABLE prompt_versions (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    prompt_key   text        NOT NULL,                     -- chat.system · reaction.lines · engine.actor 등 (PromptKey)
    character_id bigint      REFERENCES characters (id),   -- NULL = 모든 캐릭터 공통. 캐릭터 전용 ACTIVE가 있으면 그쪽이 먼저
    version      int         NOT NULL CHECK (version > 0), -- (키, 캐릭터)마다 1부터
    status       text        NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    body         text        NOT NULL,
    note         text,                                     -- 무엇을 왜 바꿨는지
    created_at   timestamptz NOT NULL DEFAULT now(),
    activated_at timestamptz,
    UNIQUE NULLS NOT DISTINCT (prompt_key, character_id, version)
);
CREATE UNIQUE INDEX uq_prompt_versions_active ON prompt_versions (prompt_key, character_id) NULLS NOT DISTINCT
    WHERE status = 'ACTIVE';

-- 대화 엔진(ENGINE 파이프라인) 상태. 세션 × 캐릭터마다 하나, 턴이 성공했을 때만 저장한다.
-- 세션이 끝나면 지운다 (세션을 넘어 남는 건 conversations.summary뿐)
CREATE TABLE dialogue_states (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id     bigint      NOT NULL REFERENCES study_sessions (id),
    character_id   bigint      NOT NULL REFERENCES characters (id),
    engine_version int         NOT NULL,                   -- 상태 JSON 형식 버전. 엔진이 형식을 바꾸면 올리고, 옛 상태는 새로 시작
    state          jsonb       NOT NULL,                   -- engine.DialogueState 그대로
    version        bigint      NOT NULL DEFAULT 0,         -- 낙관적 잠금 (같은 세션 메시지가 겹쳐도 덮어쓰지 않게)
    updated_at     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (session_id, character_id)
);

-- llm_usages: 사용량 → LLM 호출 한 번의 기록. 비용 근거에 더해 품질·지연 분석에 쓴다.
-- 실험실·평가 호출은 사용자·세션이 없어서 NULL 허용. 아직 쓰는 코드가 없어 행이 없으므로 기본값 없이 NOT NULL로 더한다.
-- input_tokens = 캐시에 없던 입력 토큰 (캐시 토큰은 cache_* 칸에 따로)
ALTER TABLE llm_usages ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE llm_usages ALTER COLUMN session_id DROP NOT NULL;
ALTER TABLE llm_usages DROP CONSTRAINT llm_usages_purpose_check;
ALTER TABLE llm_usages ADD CONSTRAINT llm_usages_purpose_check CHECK (purpose IN (
    'CHAT', 'DIRECTOR', 'ACTOR', 'JUDGE', 'REACTION', 'SUMMARY', 'CLOSING', 'EVAL_JUDGE', 'DECIDE'));
ALTER TABLE llm_usages
    ADD COLUMN source             text           NOT NULL CHECK (source IN ('APP', 'LAB', 'EVAL')),
    ADD COLUMN trace_id           uuid           NOT NULL,  -- 한 요청(대화 한 턴 등)에서 나간 호출을 묶음
    ADD COLUMN provider           text           NOT NULL,  -- anthropic · openai · jev · fake
    ADD COLUMN status             text           NOT NULL CHECK (status IN ('OK', 'ERROR')),
    ADD COLUMN error              text,
    ADD COLUMN stop_reason        text,
    ADD COLUMN cache_read_tokens  int            NOT NULL CHECK (cache_read_tokens >= 0),
    ADD COLUMN cache_write_tokens int            NOT NULL CHECK (cache_write_tokens >= 0),
    ADD COLUMN cost_usd           numeric(12, 6) NOT NULL,
    ADD COLUMN latency_ms         int            NOT NULL,
    ADD COLUMN ttft_ms            int,                       -- 스트리밍일 때 첫 글자까지
    ADD COLUMN prompt_version_ids bigint[]       NOT NULL,  -- 이 호출에 쓴 prompt_versions.id
    ADD COLUMN request            jsonb,                     -- 보낸 프롬프트 원문: 실험실·평가, 또는 capsule.llm.log-content=true일 때만
    ADD COLUMN response           text;
CREATE INDEX idx_llm_usages_trace ON llm_usages (trace_id);
