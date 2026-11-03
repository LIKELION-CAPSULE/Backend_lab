-- 로컬·테스트 전용 예시 시드 (skeleton-plan A6). local/test 프로필에서만 Flyway가 읽는다. 배포 DB에는 들어가지 않음
-- 반복 마이그레이션(R__): 내용이 바뀌면 다시 실행되므로 다시 실행해도 안전하게 쓴다
-- id는 명세 예시(약관 버전 3, 4)와 맞춘다. 실제 약관 문구는 정식 마이그레이션으로 따로 넣을 것

INSERT INTO terms (id, code, name, is_required, sort_order) OVERRIDING SYSTEM VALUE
VALUES (1, 'SERVICE', '이용약관', true, 1),
       (2, 'PRIVACY', '개인정보 처리방침', true, 2)
ON CONFLICT (id) DO UPDATE SET code = EXCLUDED.code, name = EXCLUDED.name,
    is_required = EXCLUDED.is_required, sort_order = EXCLUDED.sort_order;

INSERT INTO term_versions (id, term_id, version, content, requires_reconsent, effective_at) OVERRIDING SYSTEM VALUE
VALUES (3, 1, 1, '(이용약관 본문 준비 중)', true, '2026-11-01 00:00:00+09'),
       (4, 2, 1, '(개인정보 처리방침 본문 준비 중)', true, '2026-11-01 00:00:00+09')
ON CONFLICT (id) DO UPDATE SET term_id = EXCLUDED.term_id, version = EXCLUDED.version, content = EXCLUDED.content,
    requires_reconsent = EXCLUDED.requires_reconsent, effective_at = EXCLUDED.effective_at;
