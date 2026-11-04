-- 모듈: dialogue (담당: seunghee)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 세계관(세계 · 지식 카드 · 사건) · 대화 기술을 뺀다 (ADR 0022 결정 19부터 답장에 안 씀, 코드에서도 읽지 않음)
DROP TABLE character_moves;
ALTER TABLE character_profiles DROP COLUMN world_id; -- worlds FK도 같이 지워진다
DROP TABLE world_events;
DROP TABLE world_lore;
DROP TABLE worlds;

-- 한 칸에 계속 합치던 요약: conversation_recaps로 바뀐 뒤(결정 14) 읽고 쓰는 곳이 없다
ALTER TABLE conversations DROP COLUMN summary, DROP COLUMN summary_updated_at;

-- 캐릭터 설정 버전: profile.yml의 version이 이 값보다 높으면 앱 시작 때 파일로 다시 불러온다 (CharacterContentSeeder)
ALTER TABLE character_profiles ADD COLUMN content_version integer NOT NULL DEFAULT 0;
