-- 모듈: relation (담당: seunghee)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 출처: docs/specs/schema.sql 섹션 5·6·7에 흩어진 관계·해금 테이블 (2026-11-03 노션 스냅샷)

-- 사용자가 연 배경 (해금 판정 규칙은 서버: 한 명이라도 / 모두 / 합계 중 미정)
CREATE TABLE user_unlocked_backgrounds (
    user_id       bigint      NOT NULL REFERENCES users (id),
    background_id bigint      NOT NULL REFERENCES room_backgrounds (id) ON DELETE CASCADE,
    unlocked_at   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, background_id)
);

-- 사용자 × 캐릭터 관계: 처음 만날 때 행을 만듦. 행이 없으면 아직 안 만난 것
-- 호감도는 세션이 끝나도 여기에 계속 남음 (사용자마다 다르므로 characters가 아니라 여기)
CREATE TABLE user_character_relations (
    id                  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             bigint      NOT NULL REFERENCES users (id),
    character_id        bigint      NOT NULL REFERENCES characters (id),
    relation_points     int         NOT NULL DEFAULT 0,    -- 호감도 (갱신은 relation_points = relation_points + n)
    level               int         NOT NULL DEFAULT 1,    -- 저장해 둠: 기준이 바뀌어도 이미 오른 단계는 유지
    total_study_seconds int         NOT NULL DEFAULT 0,    -- 함께한 공부 시간 (쉬는 시간 제외, 세션 합계와 같아야 함)
    first_met_at        timestamptz NOT NULL DEFAULT now(),
    last_met_at         timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, character_id)
);
CREATE INDEX idx_relations_character ON user_character_relations (character_id);

CREATE TABLE user_unlocked_contents (
    user_id     bigint      NOT NULL REFERENCES users (id),
    content_id  bigint      NOT NULL REFERENCES character_contents (id) ON DELETE CASCADE,
    unlocked_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, content_id)
);

-- 호감도 변화 이력 (계속 남음). 대화 점수는 LLM이 주고 서버가 범위 안으로 자른 뒤 기록
--   SESSION_EVENT: 공부 중 이벤트(자리 비움, 복귀 등) 하나로 바뀐 점수. 1:3 룸이면 캐릭터마다 한 행씩
--   EVENT: 기간 한정 이벤트(character_contents의 EVENT)로 받은 점수
CREATE TABLE relation_logs (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    relation_id      bigint      NOT NULL REFERENCES user_character_relations (id),
    session_id       bigint      REFERENCES study_sessions (id),
    session_event_id bigint      REFERENCES session_events (id),   -- SESSION_EVENT일 때 원인 이벤트
    request_id       uuid,                                      -- CHAT일 때 메시지 전송 요청 id (메시지는 지워지므로 FK 아님)
    delta            int         NOT NULL,                      -- 음수 가능
    reason           text        NOT NULL CHECK (reason IN (
                         'STUDY_TIME', 'STREAK', 'FOCUS_BONUS', 'FIRST_MEET', 'EVENT', 'CHAT', 'SESSION_EVENT')),
    created_at       timestamptz NOT NULL DEFAULT now(),
    CHECK ((reason = 'CHAT') = (request_id IS NOT NULL)),
    CHECK ((reason = 'SESSION_EVENT') = (session_event_id IS NOT NULL)),
    CHECK (delta <> 0),                                        -- 0점이면 기록하지 않음
    CHECK (reason <> 'CHAT' OR delta BETWEEN -5 AND 5)         -- 대화 한 번의 점수 범위 (기획에 맞게 조정)
);
CREATE INDEX idx_relation_logs_relation ON relation_logs (relation_id, created_at DESC);
CREATE UNIQUE INDEX uq_relation_logs_request
    ON relation_logs (relation_id, request_id) WHERE request_id IS NOT NULL;              -- 같은 요청은 한 번만 반영
CREATE UNIQUE INDEX uq_relation_logs_session_event
    ON relation_logs (relation_id, session_event_id) WHERE session_event_id IS NOT NULL;  -- 같은 이벤트는 캐릭터마다 한 번만
