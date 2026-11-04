-- 로컬·테스트 전용 예시 시드 (skeleton-plan A6). local/test 프로필에서만 Flyway가 읽는다. 배포 DB에는 들어가지 않음
-- 반복 마이그레이션(R__): 내용이 바뀌면 다시 실행되므로 다시 실행해도 안전하게 쓴다
-- 값은 api.md 예시(룸 1 · 캐릭터 1 · 기본 아바타). 캐릭터 1은 첫 캐릭터 말포이(PM 2026-11-04, 모델 · 이미지 주소는 그대로).
--   화면 문구(성격 · 소개 · 룸 · 배경 이름)는 말포이 설정(characters/malfoy/profile.yml의 세계 · 바이블)에 맞춤
-- 이미지 주소는 가짜(.invalid). 실제 에셋은 정식 마이그레이션으로 따로 넣을 것

INSERT INTO live2d_models (id, storage_prefix, model_json_path, scale, offset_x, offset_y) OVERRIDING SYSTEM VALUE
VALUES (1, 'live2d/chaerin/v1/', 'chaerin.model3.json', 1.00, 0, 12),
       (2, 'live2d/avatar1/v1/', 'avatar1.model3.json', 1.00, 0, 0)
ON CONFLICT (id) DO UPDATE SET storage_prefix = EXCLUDED.storage_prefix, model_json_path = EXCLUDED.model_json_path,
    scale = EXCLUDED.scale, offset_x = EXCLUDED.offset_x, offset_y = EXCLUDED.offset_y;

INSERT INTO live2d_motions (id, model_id, group_name, name, file_path) OVERRIDING SYSTEM VALUE
VALUES (1, 1, 'Idle', '두리번거리기', 'motions/look_around.motion3.json'),
       (2, 1, 'TapBody', '손 흔들기', 'motions/wave.motion3.json')
ON CONFLICT (id) DO UPDATE SET model_id = EXCLUDED.model_id, group_name = EXCLUDED.group_name,
    name = EXCLUDED.name, file_path = EXCLUDED.file_path;

INSERT INTO live2d_expressions (id, model_id, name, file_path) OVERRIDING SYSTEM VALUE
VALUES (1, 1, '삐짐', 'exp/pout.exp3.json'),
       (2, 1, '웃음', 'exp/smile.exp3.json')
ON CONFLICT (id) DO UPDATE SET model_id = EXCLUDED.model_id, name = EXCLUDED.name, file_path = EXCLUDED.file_path;

-- trigger는 api.md 19의 서버 enum (GREETING · AWAY · RETURN · BREAK · BREAK_END)
INSERT INTO live2d_reaction_maps (id, model_id, trigger_type, intensity, motion_id, expression_id) OVERRIDING SYSTEM VALUE
VALUES (1, 1, 'GREETING', NULL, 2, 2),
       (2, 1, 'AWAY', 1, 1, NULL),
       (3, 1, 'AWAY', 2, 1, 1),
       (4, 1, 'AWAY', 3, NULL, 1),
       (5, 1, 'RETURN', NULL, NULL, 2),
       (6, 1, 'BREAK', NULL, 2, NULL),
       (7, 1, 'BREAK_END', NULL, NULL, 2)
ON CONFLICT (id) DO UPDATE SET model_id = EXCLUDED.model_id, trigger_type = EXCLUDED.trigger_type,
    intensity = EXCLUDED.intensity, motion_id = EXCLUDED.motion_id, expression_id = EXCLUDED.expression_id;

INSERT INTO avatars (id, model_id, name, thumbnail_url, sort_order) OVERRIDING SYSTEM VALUE
VALUES (1, 2, '기본 아바타', 'https://cdn.capsule.invalid/avatar1.png', 1)
ON CONFLICT (id) DO UPDATE SET model_id = EXCLUDED.model_id, name = EXCLUDED.name,
    thumbnail_url = EXCLUDED.thumbnail_url, sort_order = EXCLUDED.sort_order;

INSERT INTO study_rooms (id, name, description, thumbnail_url, sort_order) OVERRIDING SYSTEM VALUE
VALUES (1, '밤의 호그와트 도서관', '지기 싫어하는 슬리데린 라이벌 말포이와 촛불 아래에서 같이 공부해요.',
        'https://cdn.capsule.invalid/room1.png', 1)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description,
    thumbnail_url = EXCLUDED.thumbnail_url, sort_order = EXCLUDED.sort_order;

INSERT INTO room_backgrounds (id, room_id, name, image_url, is_default, sort_order) OVERRIDING SYSTEM VALUE
VALUES (1, 1, '밤의 도서관', 'https://cdn.capsule.invalid/room1-bg.png', true, 1)
ON CONFLICT (id) DO UPDATE SET room_id = EXCLUDED.room_id, name = EXCLUDED.name, image_url = EXCLUDED.image_url,
    is_default = EXCLUDED.is_default, sort_order = EXCLUDED.sort_order;

INSERT INTO characters (id, room_id, model_id, name, personality, intro, profile_image_url, sort_order)
    OVERRIDING SYSTEM VALUE
VALUES (1, 1, 1, '말포이', '{츤데레, 슬리데린 5학년, 라이벌}', '지기 싫어서 일부러 내 옆자리에 앉는 슬리데린 라이벌',
        'https://cdn.capsule.invalid/chaerin.png', 1)
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
