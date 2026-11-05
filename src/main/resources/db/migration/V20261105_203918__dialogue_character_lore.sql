-- 모듈: dialogue (담당: seunghee)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 로어북: 사용자 말에 keys 중 하나가 있을 때만 답장 프롬프트에 넣는 원작 설정 카드 (최대 2장)
--   [{"title": "해리 포터", "keys": ["해리", "포터"], "text": "그리핀도르 학생이자 오랜 앙숙. ..."}, ...]
ALTER TABLE character_profiles ADD COLUMN lore jsonb NOT NULL DEFAULT '[]';
