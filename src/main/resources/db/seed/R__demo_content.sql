-- 로컬·테스트 전용 예시 시드 (skeleton-plan A6). local/test 프로필에서만 Flyway가 읽는다. 배포 DB에는 들어가지 않음
-- 반복 마이그레이션(R__): 내용이 바뀌면 다시 실행되므로 다시 실행해도 안전하게 쓴다
-- 값은 api.md 예시(룸 1 · 캐릭터 1 · 기본 아바타). 캐릭터 1은 첫 캐릭터 말포이(PM 2026-11-04, 모델 · 이미지 주소는 그대로).
--   화면 문구(성격 · 소개 · 룸 · 배경 이름)는 말포이 설정(characters/malfoy/profile.yml의 세계 · 바이블)에 맞춤
-- 룸 1 썸네일 · 배경, 캐릭터 1 프로필은 프론트 에셋 전달본(2026-11-05)의 실제 이미지 (Supabase 버킷 live2d-model/images/,
--   전달본 runtime/ 경로 그대로): 야간 도서관 포스터 · 야간 도서관 배경 · 금발 채린 그림. 배포 DB는 정식 마이그레이션으로 따로 넣을 것
--   상세 배너(room-detail-banners/night-detail-banner-v1) · 홈 카드 전경(scenes/home-v2/night-home-subject-v2)도 버킷에 있지만 API 칸이 없어 DB에 넣지 않음
-- 이미지 칸은 CDN 상대 경로로 넣는다. 서버가 CAPSULE_CDN_BASE_URL을 붙여 내려줌 (schema.sql 이미지 칸 규칙, #105)
-- 캐릭터 1 Live2D(모델 1)는 실제 에셋 (2026-11-05, Supabase 버킷 live2d-model의 chaerin/v1/, #9).
--   CAPSULE_CDN_BASE_URL=https://<project-ref>.supabase.co/storage/v1/object/public/live2d-model 이면 그대로 열린다
--   모션 파일은 없음 (api.md 19: 말포이 동작은 기본 동작뿐, motion은 보통 null). 반응은 표정만

-- 옛 가짜 모션 · 반응 행을 지우고 다시 넣는다 (반복 마이그레이션이라 이미 들어간 DB에도 맞춰지게)
DELETE FROM live2d_reaction_maps WHERE model_id = 1;
DELETE FROM live2d_motions WHERE model_id = 1;

INSERT INTO live2d_models (id, storage_prefix, model_json_path, scale, offset_x, offset_y) OVERRIDING SYSTEM VALUE
VALUES (1, 'chaerin/v1/', 'chaerin.model3.json', 1.00, 0, 12),
       (2, 'haru/v1/', 'Haru.model3.json', 1.00, 0, 0) -- 아바타: Live2D 공식 샘플 Haru (무료 소재 라이선스, 출처 표기 필요, 음성 참조 제거)
ON CONFLICT (id) DO UPDATE SET storage_prefix = EXCLUDED.storage_prefix, model_json_path = EXCLUDED.model_json_path,
    scale = EXCLUDED.scale, offset_x = EXCLUDED.offset_x, offset_y = EXCLUDED.offset_y;

-- 표정 8개: 이름 = 감정 이름 (api.md 19 말포이 표정 목록, 디자이너 파일 expression1~8 순서)
INSERT INTO live2d_expressions (id, model_id, name, file_path) OVERRIDING SYSTEM VALUE
VALUES (1, 1, '삐짐', 'exp/expression1.exp3.json'),
       (2, 1, '행복', 'exp/expression2.exp3.json'),
       (3, 1, '경멸', 'exp/expression3.exp3.json'),
       (4, 1, '하품', 'exp/expression4.exp3.json'),
       (5, 1, '화남', 'exp/expression5.exp3.json'),
       (6, 1, '웃음', 'exp/expression6.exp3.json'),
       (7, 1, '수줍음', 'exp/expression7.exp3.json'),
       (8, 1, '집중', 'exp/expression8.exp3.json')
ON CONFLICT (id) DO UPDATE SET model_id = EXCLUDED.model_id, name = EXCLUDED.name, file_path = EXCLUDED.file_path;

-- trigger: 18 응답의 '알아채는' 동작(상태 · 동작 이벤트) + 19 · 20의 감정 이름 (schema.sql live2d_reaction_maps)
-- 이벤트별 표정은 예시값 (실제 연출은 정식 시드 #19에서)
INSERT INTO live2d_reaction_maps (id, model_id, trigger_type, intensity, motion_id, expression_id) OVERRIDING SYSTEM VALUE
VALUES (1, 1, 'GREETING', NULL, NULL, 6),
       (2, 1, 'AWAY', 1, NULL, 3),
       (3, 1, 'AWAY', 2, NULL, 1),
       (4, 1, 'AWAY', 3, NULL, 1),
       (5, 1, 'RETURN', NULL, NULL, 6),
       (6, 1, 'BREAK', NULL, NULL, 2),
       (7, 1, 'BREAK_END', NULL, NULL, 8),
       (8, 1, 'DROWSY', NULL, NULL, 4),
       (9, 1, 'YAWN', NULL, NULL, 4),
       (10, 1, 'WAVE', NULL, NULL, 6),
       (11, 1, 'HEAD_TURN', NULL, NULL, 3),
       -- 감정 이름 → 같은 이름의 표정 (19 · 20, #9)
       (12, 1, '삐짐', NULL, NULL, 1),
       (13, 1, '행복', NULL, NULL, 2),
       (14, 1, '경멸', NULL, NULL, 3),
       (15, 1, '하품', NULL, NULL, 4),
       (16, 1, '화남', NULL, NULL, 5),
       (17, 1, '웃음', NULL, NULL, 6),
       (18, 1, '수줍음', NULL, NULL, 7),
       (19, 1, '집중', NULL, NULL, 8)
ON CONFLICT (id) DO UPDATE SET model_id = EXCLUDED.model_id, trigger_type = EXCLUDED.trigger_type,
    intensity = EXCLUDED.intensity, motion_id = EXCLUDED.motion_id, expression_id = EXCLUDED.expression_id;

INSERT INTO avatars (id, model_id, name, thumbnail_url, sort_order) OVERRIDING SYSTEM VALUE
VALUES (1, 2, '기본 아바타',
        'haru/thumbnail.png', 1)
ON CONFLICT (id) DO UPDATE SET model_id = EXCLUDED.model_id, name = EXCLUDED.name,
    thumbnail_url = EXCLUDED.thumbnail_url, sort_order = EXCLUDED.sort_order;

INSERT INTO study_rooms (id, name, description, thumbnail_url, sort_order) OVERRIDING SYSTEM VALUE
VALUES (1, '밤의 호그와트 도서관', '지기 싫어하는 슬리데린 라이벌 말포이와 촛불 아래에서 같이 공부해요.',
        'images/rooms/night-study.webp', 1)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description,
    thumbnail_url = EXCLUDED.thumbnail_url, sort_order = EXCLUDED.sort_order;

INSERT INTO room_backgrounds (id, room_id, name, image_url, is_default, sort_order) OVERRIDING SYSTEM VALUE
VALUES (1, 1, '밤의 도서관',
        'images/scenes/night-background-v1.webp',
        true, 1)
ON CONFLICT (id) DO UPDATE SET room_id = EXCLUDED.room_id, name = EXCLUDED.name, image_url = EXCLUDED.image_url,
    is_default = EXCLUDED.is_default, sort_order = EXCLUDED.sort_order;

INSERT INTO characters (id, room_id, model_id, name, personality, intro, profile_image_url, sort_order)
    OVERRIDING SYSTEM VALUE
VALUES (1, 1, 1, '말포이', '{츤데레, 슬리데린 5학년, 라이벌}', '지기 싫어서 일부러 내 옆자리에 앉는 슬리데린 라이벌',
        'images/characters/kang-study.webp', 1)
ON CONFLICT (id) DO UPDATE SET room_id = EXCLUDED.room_id, model_id = EXCLUDED.model_id, name = EXCLUDED.name,
    personality = EXCLUDED.personality, intro = EXCLUDED.intro, profile_image_url = EXCLUDED.profile_image_url,
    sort_order = EXCLUDED.sort_order;

-- id를 직접 넣었으므로 시퀀스를 맞춘다 (안 하면 다음 INSERT가 id 1부터 시작해 충돌)
SELECT setval(pg_get_serial_sequence('live2d_models', 'id'), (SELECT max(id) FROM live2d_models));
SELECT setval(pg_get_serial_sequence('live2d_motions', 'id'), (SELECT max(id) FROM live2d_motions));
SELECT setval(pg_get_serial_sequence('live2d_expressions', 'id'), (SELECT max(id) FROM live2d_expressions));
SELECT setval(pg_get_serial_sequence('live2d_reaction_maps', 'id'), (SELECT max(id) FROM live2d_reaction_maps));
SELECT setval(pg_get_serial_sequence('avatars', 'id'), (SELECT max(id) FROM avatars));
SELECT setval(pg_get_serial_sequence('study_rooms', 'id'), (SELECT max(id) FROM study_rooms));
SELECT setval(pg_get_serial_sequence('room_backgrounds', 'id'), (SELECT max(id) FROM room_backgrounds));
SELECT setval(pg_get_serial_sequence('characters', 'id'), (SELECT max(id) FROM characters));
