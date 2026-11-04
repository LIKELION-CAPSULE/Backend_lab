-- 모듈: dialogue (담당: seunghee)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 대화 기억 세 겹 (ADR 0021). 크기가 정해져 있어 세션이 쌓여도 프롬프트가 커지지 않는다
--   ① 아는 사실 character_memories: 매 턴 결정 모델이 추가 · 고치기 · 지우기를 고름. 최대 20개(넘치면 하나 버림)
--   ② 지난 세션 흐름 conversation_recaps: 세션이 끝날 때 그 세션만 카드 한 장. 최근 5장만 두고 다시 압축하지 않음
--   ③ 이번 세션 원문 chat_messages: 최근 20줄 (그대로)
-- conversations.summary(한 칸에 계속 합치던 요약)는 더 쓰지 않는다. 컬럼 삭제는 다음 단계
CREATE TABLE character_memories (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           bigint      NOT NULL REFERENCES users (id),
    character_id      bigint      NOT NULL REFERENCES characters (id),
    content           text        NOT NULL CHECK (char_length(content) BETWEEN 1 AND 80),  -- 한 줄 (프롬프트는 40자 이내로 씀)
    source_request_id uuid,                                    -- 이 기억을 만든 · 고친 메시지 요청 (메시지는 지워지므로 FK 아님)
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_character_memories_owner ON character_memories (user_id, character_id);

CREATE TABLE conversation_recaps (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id bigint      NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    session_id      bigint      NOT NULL,                     -- 다른 모듈(session) 테이블이라 FK 아님
    content         text        NOT NULL CHECK (char_length(content) BETWEEN 1 AND 200),
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (conversation_id, session_id)
);

-- 사용자 말의 종류 (relation SignalType). 같은 세션 안 반복 규칙(고민 1번, 무례 반복, 같은 칭찬 연타)에 쓴다
ALTER TABLE chat_messages ADD COLUMN signal text;

ALTER TABLE llm_usages DROP CONSTRAINT llm_usages_purpose_check;
ALTER TABLE llm_usages ADD CONSTRAINT llm_usages_purpose_check CHECK (purpose IN (
    'CHAT', 'DIRECTOR', 'ACTOR', 'JUDGE', 'REACTION', 'SUMMARY', 'CLOSING', 'EVAL_JUDGE', 'DECIDE', 'MEMORY'));
