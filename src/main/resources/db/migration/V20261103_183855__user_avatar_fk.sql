-- 모듈: user (담당: seonguk)
-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.
-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.

-- 출처: docs/specs/schema.sql 섹션 4 끝 (avatars가 content_init_schema에서 생긴 뒤에 FK 연결)
ALTER TABLE users
    ADD FOREIGN KEY (avatar_id) REFERENCES avatars (id);
