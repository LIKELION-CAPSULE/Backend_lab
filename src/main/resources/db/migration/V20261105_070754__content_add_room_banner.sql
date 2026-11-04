-- 모듈: content (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 룸 상세(H04) 가로 배너 (api.md 12 bannerUrl, 2026-11-05). CDN 상대 경로 또는 전체 URL. NULL이면 프론트가 thumbnail_url
ALTER TABLE study_rooms ADD COLUMN banner_url text;

