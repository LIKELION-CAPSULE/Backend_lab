-- 모듈: dialogue (담당: seunghee)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- LLM 실험실 (내부 품질 테스트, capsule.llm.lab.enabled=true일 때만 열림, ADR 0021). 서비스 데이터와 섞이지 않게 따로 둔다

-- 실험 대화방: 캐릭터 · 파이프라인 · 실험 조건(공급자·모델·프롬프트 버전 고정·가상 사용자 정보)을 정해 두고 계속 말을 건다
CREATE TABLE llm_lab_sessions (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title        text        NOT NULL,
    character_id bigint      NOT NULL REFERENCES characters (id),
    pipeline     text        NOT NULL CHECK (pipeline IN ('SIMPLE', 'ENGINE')),
    config       jsonb       NOT NULL,   -- LabConfig
    state        jsonb,                  -- ENGINE 파이프라인 상태 (engine.DialogueState)
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);

-- 실험 대화 한 턴. 이 턴의 LLM 호출들은 llm_usages.trace_id로 찾는다
CREATE TABLE llm_lab_turns (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lab_session_id bigint         NOT NULL REFERENCES llm_lab_sessions (id) ON DELETE CASCADE,
    trace_id       uuid           NOT NULL,
    user_text      text           NOT NULL,
    reply          text,                    -- 실패하면 NULL (error에 이유)
    emotion        text,
    affinity       int,
    error          text,
    latency_ms     int            NOT NULL,
    cost_usd       numeric(12, 6) NOT NULL,
    trace          jsonb          NOT NULL, -- 파이프라인 중간 결과 (쓴 프롬프트 버전, 엔진의 감독 계획·후보·감점 등)
    rating         int CHECK (rating BETWEEN 1 AND 5), -- 사람 평가
    rating_note    text,
    created_at     timestamptz    NOT NULL DEFAULT now()
);
CREATE INDEX idx_llm_lab_turns_session ON llm_lab_turns (lab_session_id, id);

-- 평가 케이스 (골든 대본): 사용자 말 목록. 같은 케이스를 조건만 바꿔 돌려 프롬프트 버전·모델을 비교한다
CREATE TABLE llm_eval_cases (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         text        NOT NULL,
    character_id bigint      NOT NULL REFERENCES characters (id),
    turns        jsonb       NOT NULL,   -- ["사용자 말 1", "사용자 말 2", ...]
    context      jsonb       NOT NULL,   -- 가상 사용자 정보 (LabConfig 중 닉네임·호감도 단계·요약·일정)
    rubric       text,                   -- 심사 기준 (LLM 심사관에게 그대로 줌)
    created_at   timestamptz NOT NULL DEFAULT now()
);

-- 평가 실행: 케이스 묶음 × 반복 횟수를 한 조건으로 돌린다
CREATE TABLE llm_eval_runs (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    label       text        NOT NULL,
    pipeline    text        NOT NULL CHECK (pipeline IN ('SIMPLE', 'ENGINE')),
    config      jsonb       NOT NULL,   -- LabConfig (공급자·모델·프롬프트 버전 고정·엔진 옵션)
    case_ids    bigint[]    NOT NULL,   -- 돌릴 케이스 (진행률 = 결과 수 / (케이스 수 × repeats))
    repeats     int         NOT NULL CHECK (repeats BETWEEN 1 AND 20),
    judge       boolean     NOT NULL,   -- LLM 심사 여부
    status      text        NOT NULL CHECK (status IN ('RUNNING', 'DONE', 'FAILED')),
    created_at  timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz
);

-- 케이스 × 반복 한 번의 결과
CREATE TABLE llm_eval_results (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id      bigint         NOT NULL REFERENCES llm_eval_runs (id) ON DELETE CASCADE,
    case_id     bigint         NOT NULL REFERENCES llm_eval_cases (id) ON DELETE CASCADE,
    repeat_no   int            NOT NULL,
    trace_id    uuid           NOT NULL,
    transcript  jsonb          NOT NULL, -- [{user, reply, emotion, affinity, latencyMs, error}]
    judge_score numeric(4, 1),           -- LLM 심사 0~10
    judge_note  text,
    human_score int CHECK (human_score BETWEEN 1 AND 5),
    human_note  text,
    latency_ms  int            NOT NULL, -- 턴 지연의 합
    cost_usd    numeric(12, 6) NOT NULL,
    error       text,
    created_at  timestamptz    NOT NULL DEFAULT now()
);
CREATE INDEX idx_llm_eval_results_run ON llm_eval_results (run_id, case_id, repeat_no);
