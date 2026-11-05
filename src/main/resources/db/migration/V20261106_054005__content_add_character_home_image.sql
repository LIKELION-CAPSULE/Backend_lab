-- 모듈: content (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 홈(H03) 포스터 카드에 겹치는 캐릭터 전신 그림 (api.md 11 homeImageUrl, 2026-11-06). CDN 상대 경로 또는 전체 URL. 홈에서만 씀
ALTER TABLE characters ADD COLUMN home_image_url text;

