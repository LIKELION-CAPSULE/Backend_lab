-- 모듈: dialogue (담당: seunghee)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 실험실에서 고친 캐릭터 설정은 profile.yml 버전이 올라가도 시작할 때 덮지 않는다. NULL이면 파일 값 그대로
ALTER TABLE character_profiles ADD COLUMN lab_edited_at timestamptz;

-- 표정 대체 표 (ADR 0028 확장 → 제거의 제거): #134 배포 뒤 아무 코드도 읽지 않는다
ALTER TABLE character_profiles DROP COLUMN expression_fallbacks;
