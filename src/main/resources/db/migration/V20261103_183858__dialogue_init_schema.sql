-- 모듈: dialogue (담당: seunghee)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 출처: docs/specs/schema.sql 섹션 8 (2026-11-03 노션 스냅샷)

-- ---------------------------------------------------------------------
-- 8. 대화 LLM (S08)
-- ---------------------------------------------------------------------
-- 사용자 × 캐릭터마다 하나. 세션을 넘어 이어지는 건 요약뿐. 탈퇴 시 서버가 삭제
CREATE TABLE conversations (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id            bigint      NOT NULL REFERENCES users (id),
    character_id       bigint      NOT NULL REFERENCES characters (id),
    summary            text,                              -- 지난 세션들까지의 대화 요약
    summary_updated_at timestamptz,
    created_at         timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, character_id)
);

-- 현재 세션의 메시지만 보관. 세션이 끝나면 요약에 합친 뒤 한 트랜잭션에서 삭제 (요약에 실패하면 남김)
-- 음성(TTS)은 저장하지 않고 응답으로 바로 전달
CREATE TABLE chat_messages (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id bigint      NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    session_id      bigint      NOT NULL REFERENCES study_sessions (id),
    request_id      uuid        NOT NULL,                 -- 사용자 메시지와 그 답변이 같은 값
    sender          text        NOT NULL CHECK (sender IN ('USER', 'CHARACTER')),
    content         text        NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (conversation_id, request_id, sender)          -- 재전송돼도 질문·답변 각각 한 번만
);
CREATE INDEX idx_chat_messages_session ON chat_messages (session_id, id);

-- LLM 사용량 (지우지 않고 계속 쌓임, 비용·크레딧 근거)
CREATE TABLE llm_usages (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       bigint      NOT NULL REFERENCES users (id),
    session_id    bigint      NOT NULL REFERENCES study_sessions (id),
    purpose       text        NOT NULL CHECK (purpose IN ('CHAT', 'AFFINITY', 'SUMMARY')),  -- 답변 / 호감도 점수 판정 / 세션 종료 요약
    model         text        NOT NULL,
    input_tokens  int         NOT NULL CHECK (input_tokens >= 0),
    output_tokens int         NOT NULL CHECK (output_tokens >= 0),
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_llm_usages_user ON llm_usages (user_id, created_at);
