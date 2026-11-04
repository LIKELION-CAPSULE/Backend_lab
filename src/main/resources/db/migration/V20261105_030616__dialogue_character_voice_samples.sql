-- 모듈: dialogue (담당: seunghee)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 관계 단계별 말투 샘플 (ADR 0022 결정 19). 답장 · 상태 반응의 고정 블록에 지금 단계 묶음만 넣는다.
--   [{"minStage": 1, "user": "안녕", "reply": "…어. 왔으면 조용히 앉아."}, ...]  {name}은 닉네임으로 바뀜
ALTER TABLE character_profiles ADD COLUMN samples jsonb NOT NULL DEFAULT '[]';
