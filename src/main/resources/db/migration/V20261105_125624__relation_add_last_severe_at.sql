-- 모듈: relation (담당: seunghee)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 심한 말(HATE_OR_SEXUAL)을 받은 마지막 시각. 그 한국 날짜에는 대화로 오르지 않는다 (호감도 최종안).
-- 전에는 relation_logs의 signal로 판단했는데, 0점에서 심한 말을 하면 실제로 깎인 값이 0이라
-- 기록이 남지 않아(CHECK delta <> 0) 그날 오름 멈춤이 걸리지 않았다
ALTER TABLE user_character_relations ADD COLUMN last_severe_at timestamptz;

UPDATE user_character_relations r
SET last_severe_at = l.at
FROM (SELECT relation_id, max(created_at) AS at
      FROM relation_logs
      WHERE signal = 'HATE_OR_SEXUAL'
      GROUP BY relation_id) l
WHERE l.relation_id = r.id;

