-- 모듈: dialogue (담당: seunghee)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 운영 설정 (ADR 0026): 대화 설정과 AI 공급자 · 모델 중 실험실 '운영 설정'에서 바꾼 칸만. 없는 칸은 application.yml 값
CREATE TABLE dialogue_settings (
    section    text        PRIMARY KEY CHECK (section IN ('dialogue', 'llm')),
    overrides  jsonb       NOT NULL DEFAULT '{}',
    updated_at timestamptz NOT NULL DEFAULT now()
);
