-- 모듈: relation (담당: seunghee)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 호감도 최종안 (2026-11-04, ADR 0022). 호감도와 레벨을 나눈다
--   호감도 relation_points: 0~100, 50에서 시작. 대화 · 자리 비움 · 연속 출석으로만 바뀜. 관계 단계는 이 값의 구간
--   레벨: 함께 공부한 자리 지킴 시간(total_present_seconds)으로 계산. 상한 없음
-- 운영 데이터 없음(개발 DB뿐): 옛 규칙(공부 시간 점수 · 첫 만남 보너스 · 0 시작)의 기록을 지우고 모두 50에서 다시 시작한다
DELETE FROM relation_logs;
UPDATE user_character_relations SET relation_points = 50, level = 1;

ALTER TABLE user_character_relations
    ALTER COLUMN relation_points SET DEFAULT 50,
    ADD CONSTRAINT user_character_relations_points_range CHECK (relation_points BETWEEN 0 AND 100),
    ADD COLUMN total_present_seconds int NOT NULL DEFAULT 0 CHECK (total_present_seconds >= 0),
    ADD COLUMN streak_days int NOT NULL DEFAULT 0 CHECK (streak_days >= 0),  -- 연속 출석 일수 (한국 날짜)
    ADD COLUMN last_study_date date;                                          -- 마지막으로 공부를 시작한 날 (한국 날짜)

-- 대화 기록에 말의 종류를 남긴다: 심한 말 뒤 그날 오름 멈춤 판단, 문의 대응
ALTER TABLE relation_logs ADD COLUMN signal text;  -- CHAT일 때 SignalType (예: COMPLIMENT, HATE_OR_SEXUAL)

-- 대화 한 번의 범위: 오르는 쪽은 작게, 내려가는 쪽은 비율 감점(현재 값의 %)이라 크게
DO $$
DECLARE c text;
BEGIN
    SELECT conname INTO c FROM pg_constraint
        WHERE conrelid = 'relation_logs'::regclass AND contype = 'c' AND pg_get_constraintdef(oid) LIKE '%-5%';
    IF c IS NULL THEN
        RAISE EXCEPTION '옛 대화 점수 범위 CHECK(-5~5)를 못 찾음: 그대로 두면 비율 감점 기록이 실패한다';
    END IF;
    EXECUTE format('ALTER TABLE relation_logs DROP CONSTRAINT %I', c);
END $$;
ALTER TABLE relation_logs
    ADD CONSTRAINT relation_logs_chat_range CHECK (reason <> 'CHAT' OR delta BETWEEN -100 AND 5);

-- 공부 종료 정산 때의 레벨 (리포트를 다시 볼 때 같은 값을 주려고 남긴다. 레벨은 점수 기록으로 다시 계산할 수 없음)
CREATE TABLE relation_settlements (
    relation_id     bigint      NOT NULL REFERENCES user_character_relations (id),
    session_id      bigint      NOT NULL REFERENCES study_sessions (id),
    present_seconds int         NOT NULL CHECK (present_seconds >= 0),
    level_before    int         NOT NULL,
    level_after     int         NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (relation_id, session_id)
);
