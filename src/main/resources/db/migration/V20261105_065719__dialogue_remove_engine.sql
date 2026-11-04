-- 모듈: dialogue (담당: seunghee)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 대화 엔진(ENGINE 파이프라인)을 뺀다 (ADR 0022 결정 19부터 안 씀). 엔진 상태 · 엔진 실험 기록 · 엔진 프롬프트(engine.*)도 같이.
-- 실험 대화방의 턴 · 평가 결과는 FK ON DELETE CASCADE로 같이 지워진다. llm_usages.purpose CHECK는 옛 기록 때문에 그대로 둔다
DELETE FROM llm_eval_runs WHERE pipeline = 'ENGINE';
DELETE FROM llm_lab_sessions WHERE pipeline = 'ENGINE';

ALTER TABLE llm_lab_sessions
    DROP CONSTRAINT llm_lab_sessions_pipeline_check,
    ADD CONSTRAINT llm_lab_sessions_pipeline_check CHECK (pipeline IN ('SIMPLE', 'SCENE')),
    DROP COLUMN state;

ALTER TABLE llm_eval_runs
    DROP CONSTRAINT llm_eval_runs_pipeline_check,
    ADD CONSTRAINT llm_eval_runs_pipeline_check CHECK (pipeline IN ('SIMPLE', 'SCENE'));

DROP TABLE dialogue_states;

DELETE FROM prompt_versions WHERE prompt_key LIKE 'engine.%';
