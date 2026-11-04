-- 모듈: relation (담당: seunghee)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- ADJUST: local 흐름 테스트에서 호감도를 손으로 맞춘 만큼. 50 + 기록의 합 = 지금 점수를 지키려고 기록으로 남긴다
ALTER TABLE relation_logs DROP CONSTRAINT relation_logs_reason_check;
ALTER TABLE relation_logs ADD CONSTRAINT relation_logs_reason_check CHECK (reason IN (
    'STUDY_TIME', 'STREAK', 'FOCUS_BONUS', 'FIRST_MEET', 'EVENT', 'CHAT', 'SESSION_EVENT', 'ADJUST'));
