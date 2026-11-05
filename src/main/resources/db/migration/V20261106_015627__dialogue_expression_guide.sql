-- 모듈: dialogue (담당: seunghee)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 표정은 결정 모델이 고른다 (ADR 0028): 표정마다 언제 쓰는지 (결정 모델 보기 설명)
-- expression_fallbacks는 코드가 더 읽지 않는다. 지금 떠 있는 서버가 아직 읽으므로 배포 뒤 다음 마이그레이션에서 지운다
ALTER TABLE character_profiles ADD COLUMN expression_guide jsonb NOT NULL DEFAULT '{}';
