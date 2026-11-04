-- 모듈: dialogue (담당: seunghee)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 실험실 대화방 · 평가 실행에서 SCENE 파이프라인(기본, ADR 0022 결정 15)을 고를 수 있게.
-- 처음 만들 때(V20261104_053125)는 SIMPLE · ENGINE만 있어서 SCENE 평가 실행이 CHECK에 막혔다
ALTER TABLE llm_lab_sessions DROP CONSTRAINT llm_lab_sessions_pipeline_check;
ALTER TABLE llm_lab_sessions
    ADD CONSTRAINT llm_lab_sessions_pipeline_check CHECK (pipeline IN ('SIMPLE', 'SCENE', 'ENGINE'));

ALTER TABLE llm_eval_runs DROP CONSTRAINT llm_eval_runs_pipeline_check;
ALTER TABLE llm_eval_runs
    ADD CONSTRAINT llm_eval_runs_pipeline_check CHECK (pipeline IN ('SIMPLE', 'SCENE', 'ENGINE'));
