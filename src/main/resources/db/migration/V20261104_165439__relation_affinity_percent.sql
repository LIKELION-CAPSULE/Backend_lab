-- 모듈: relation (담당: seunghee)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 호감도를 0~100으로 (%처럼 읽음, ADR 0017). 계속 쌓이는 점수가 아니라 끝이 있는 값
-- 서버는 범위를 넘는 만큼을 잘라 실제로 바뀐 만큼만 relation_logs에 남긴다. 아래 CHECK는 마지막 안전장치
-- 기존 행은 범위 안으로 맞춘다 (운영 데이터 없음. 잘린 행은 relation_logs 합과 달라진다)
UPDATE user_character_relations SET relation_points = LEAST(100, GREATEST(0, relation_points))
    WHERE relation_points NOT BETWEEN 0 AND 100;
ALTER TABLE user_character_relations
    ADD CONSTRAINT user_character_relations_points_range CHECK (relation_points BETWEEN 0 AND 100);
