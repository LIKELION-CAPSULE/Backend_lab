# 뼈대(Phase A) 계획

> skeleton 스킬 1단계 산출물. **이 문서를 사람이 확인하기 전에는 코드를 쓰지 않는다.**
> 입력: `docs/specs/api.md`(23개 API), `docs/specs/schema.sql`(25개 테이블), 2026-11-03 spec-sync 기준.
> `docs/specs/flow.md`는 없어서 5장에 흐름 초안을 넣었다.

---

## 0. 설계 브리핑

### 선택한 것과 이유

#### 스택·구조 (새로 정하지 않음, 링크만)
| 항목 | 선택 | 근거 |
|---|---|---|
| 앱 | Spring Boot (Java 21) + Spring Modulith, 모듈 = `api`(계약) + `internal`(구현) | [0001](../decisions/0001-modular-monolith.md) |
| DB | PostgreSQL + Flyway (타임스탬프 버전, 모든 환경 out-of-order), `ddl-auto: validate` | [0002](../decisions/0002-postgresql-flyway.md) |
| 짧은 값 | Redis: 리프레시 토큰, 이메일 인증번호, 가입 토큰, 로그인 잠금, 요청 횟수 제한 | [0009](../decisions/0009-redis.md) |
| 테스트 | Testcontainers (PostgreSQL 16, Redis 7). H2 안 씀 | [0003](../decisions/0003-testcontainers.md) |
| 판단·LLM | 결정적인 Fake + `TODO(judgment)` + `judgment-backlog.md` | [0007](../decisions/0007-pipeline-skeleton-first.md) |

#### 새로 쓰는 것 (확인 필요, 확정되면 ADR 0011 추가)
| 무엇 | 왜 | 버린 대안 |
|---|---|---|
| **Spring Security + `spring-boot-starter-oauth2-resource-server`** (JWT HS256 검증·발급은 같이 들어 있는 Nimbus 사용) | 명세가 "액세스 토큰 30분 + 리프레시 HttpOnly 쿠키" 방식. 스타터 하나로 검증 필터·401 처리·bcrypt(`spring-security-crypto`)가 다 들어 있음 | jjwt + 직접 만든 필터 (라이브러리가 하나 더 들고 필터를 직접 유지보수함) / 세션 쿠키 인증 (명세와 다름) |
| 리프레시 토큰 = **무작위 문자열을 Redis에 저장**, 쓸 때마다 교체 | 명세 7번 "쓸 때마다 교체", 로그아웃 시 폐기가 쉬움 | 리프레시도 JWT (폐기하려면 블랙리스트가 또 필요) |
| 외부 연동은 **포트 + Fake**: `KakaoClient`, `EmailSender` | 키·SMTP 없이 E2E가 돌아야 함. 실제 어댑터는 키를 받은 뒤 붙임 (`RestClient`, 추가 의존성 없음) | 뼈대에서 실제 카카오·메일 연동 (키가 없어 테스트 불가) |
| E2E는 **MockMvc** | 이미 `spring-boot-starter-test`에 있음 | RestAssured (의존성 추가) |
| 자동 마감 배치는 **`@Scheduled`** | 시연은 서버 1대. 기본 기능으로 충분 | Quartz·ShedLock (서버가 여러 대가 되면 그때) |

추가하지 않는 것: springdoc(Swagger. 명세가 노션에 있음), spring-boot-starter-mail(메일 발송사 정한 뒤).

#### 이 명세에 대한 설계 판단
1. **모듈 5개는 `zones.conf` 그대로** (user·content·session = 성욱, relation·dialogue = 승희). schema.sql 섹션과 거의 같고, 판단이 몰린 호감도와 LLM을 승희가 맡아 서로 기다리지 않음.
2. **모듈 의존 방향은 한쪽으로만** (Spring Modulith는 순환이 있으면 `ModularityTests`가 실패함)
   ```
   content  ←  user  ←  session  ←  dialogue
      ↑                    ↓   ↘        ↓
      └──── relation ←─────┘    └→ (relation)
   ```
   - 아래쪽(content, relation)은 위쪽을 모른다. 위쪽 모듈이 아래 모듈의 `api`를 부른다.
   - 순환이 생기는 2곳만 **아래 모듈 `api`에 인터페이스를 두고 위 모듈이 구현**한다(의존 역전):

     | 인터페이스 (둔 곳) | 구현 | 왜 |
     |---|---|---|
     | `user.api.ActiveSessionLookup` | session | `GET /api/me`(user)가 진행 중 세션을 보여줘야 하는데, session은 이미 user를 부름 |
     | `session.api.SessionClosingHandler` | dialogue | 공부 종료(session)가 대화 요약과 완료 대사(dialogue)를 같은 요청 안에서 받아야 하는데, dialogue는 이미 session을 부름 |
   - **룸 상세(`GET /api/rooms/{roomId}`)는 session 모듈에 둔다.** 룸·캐릭터(content), 관계(relation), 진행 중 세션(session)을 모으는 화면이라, 가장 위에 있는 쪽이 모아야 순환이 안 생긴다. content와 session은 둘 다 성욱 담당이라 담당 경계는 그대로다.
3. **모듈 간 호출은 거의 다 동기 `api` 호출, 이벤트는 쓰지 않는다.** 명세가 "저장 + 호감도 반영 → eventId"(18), "한 트랜잭션"(22)처럼 결과를 바로 돌려줘야 하는 흐름뿐이다. 비동기 이벤트로 하면 바로 뒤의 '이벤트 반응 받기'(19)가 아직 반영 안 된 호감도를 읽을 수 있다.
4. **DB의 모듈 간 FK는 schema.sql 그대로 둔다.** 코드는 다른 모듈 테이블을 읽지 않고(JOIN 금지) `api`로만 묻는다. FK는 한 DB 안의 무결성 장치라 빼면 손해만 있다.
5. **판단 분류 기준**: 숫자·조건이 명세에 적혀 있으면 단순(예: 10초, 5회 잠금, 6시간 자동 마감). "예시", "확정 필요", "미정", LLM이 만드는 것은 판단. 2장에 엔드포인트별로, 6장에 백로그 후보를 적었다. 승희 쪽에 10개, 성욱 쪽에 1개가 몰리는데, 모듈 나눈 이유대로다.

### 버린 대안
- 화면 조합 API 전용 모듈(BFF) 추가: 모듈 수와 zones.conf가 늘어남. 조합이 필요한 곳은 3곳뿐이라 위 2번으로 충분하다.
- 모듈 간 통신을 전부 이벤트로: 18·19·22가 결과를 바로 돌려줘야 해서 안 맞는다.
- `GET /api/me`를 session 모듈로 옮기기: `PATCH /api/me`와 경로가 둘로 갈려 헷갈린다. 인터페이스 하나가 더 싸다.
- 뼈대에서 호감도를 전부 +1로 Fake: E2E가 명세 예시(+5/−1/+1)와 다른 흐름을 보여줘서 오해가 생긴다. 명세 예시값을 Fake로 쓰고 백로그에 "확정 필요"로 남긴다.

### 사람이 확인할 것

**A. 답이 있어야 진행할 수 있는 것** (추천안으로 정해 주시면 그대로 감)

| # | 질문 | 관련 | 추천안 |
|---|---|---|---|
| A1 | `users.nickname`은 NOT NULL인데, 가입 때는 닉네임을 받지 않음(이메일·카카오 둘 다) | API 4·6, DB 1 | **결정(11/03): 닉네임은 가입할 때 받음.** DB `NOT NULL` 유지, 노션 API 4번에 `nickname` 추가함. 카카오 신규 가입자는 카카오 프로필 닉네임으로 저장(동의 항목 '닉네임' 필수, 노션 API 6번 반영). 프로필 설정(10)은 그대로(닉네임 필수, H02에서 가입 때 값을 채워 보여줌). `onboardingRequired`는 사실상 "아바타를 아직 안 고름" |
| A2 | 완료 대사 `closingLine`을 새로고침해도 같게 보여줄 저장 자리가 없음 | API 22·23 | **결정(11/03): `study_sessions.closing_line text` 추가.** 노션 DB·API 23번 반영함. 생성은 dialogue, 저장은 session(종료 때) |
| A3 | 메시지 전송(20) 응답: 설명은 "답장 + 모션·표정 + 바뀐 호감도"인데 응답 표는 `requestId`·`content`·`eventId`뿐. `eventId`가 무엇인지 불명확 | API 20 | **결정(11/03): 이벤트 반응(19)과 같은 모양.** `requestId`, `content`, `motion`, `expression`, `affinity`, `fallback`, `audioBase64`(tts일 때). `eventId`는 뺌. 노션 API 20번 반영함 |
| A4 | 이벤트 반응(19): 표는 `content`, JSON은 `line`. `requestId`는 JSON에만 있음. `intensity`를 보내는 방법이 없음 | API 19 | **결정(11/03): 요청 본문 `{ requestId, intensity, tts }`**(`intensity`는 AWAY에서만, 기본 1. `tts` 기본 false), 대사 필드는 `content`, 응답에 `requestId` 그대로. 노션 API 19번 반영함 |
| A5 | 약관 `contentUrl`: DB에는 본문(`term_versions.content`)만 있고 URL이 없음 | API 1 | **결정(11/03): 응답에 `content`(본문)를 그대로 넣음.** 노션 API 1번 반영함 |
| A6 | 시드 데이터: 룸 1개, 캐릭터 채린, 기본 아바타, Live2D 모델·모션·표정·반응 매핑, 약관 2종(본문 포함)의 실제 값이 명세에 예시로만 있음 | 전체 | **명세 예시값으로 `content` 시드 마이그레이션을 만듦**. 약관 본문은 "(약관 본문 준비 중)"으로 두고, 실제 값은 나중에 새 마이그레이션으로 교체 |

**B. 이의가 없으면 이대로 진행하는 것** (명세 안에서 서로 안 맞거나 비어 있는 곳)

| # | 내용 | 기본안 |
|---|---|---|
| B1 | `GET /api/me`를 지울지 (노션 메모 "삭제해도 될듯?") | 남김. 새로고침 시 어느 화면으로 갈지 정하는 기준이라 |
| B2 | 홈(11) 응답: 표는 `lastSession`, JSON은 `activeSession` | `activeSession` (내 정보와 같은 이름·모양) |
| B3 | 공부 시작(16) 응답: JSON 오타 `avater`, character의 `thumbnailUrl`과 `profileImageUrl` 혼용 | 표 기준: `character.profileImageUrl`, `avatar` 객체, `me` |
| B4 | `eventId`가 표는 Long, JSON은 문자열 | 숫자(Long) |
| B5 | 이어하기(17) 응답에 `focusEventId`를 넣을지 | 넣지 않음 (명세 "현재는 반응 없이 바로 공부 재개") |
| B6 | 공부 종료·리포트(22·23) 응답에 시간 값 | `studySeconds`, `presentSeconds`, `awaySeconds`를 응답에 넣음 (22번 "같이 챙길 것") |
| B7 | 프로필 이미지 출처 (`users.profile_image_url` vs 아바타 썸네일) | 아바타 썸네일 (H02 "이 모습이 프로필 이미지가 돼요"). `profile_image_url` 컬럼은 지금 안 씀 |
| B8 | 룸 상세(12)가 말하는 '캐릭터 Live2D 조회' API가 목록에 없음 | 만들지 않음. 공부 시작(16) 응답에 모델 정보가 이미 있음 |
| B9 | 일정 저장(13): `dueDate`가 필수로 표시돼 있는데 "목표는 마감일이 없을 수 있음" | `EXAM`·`PROMISE`는 필수, `GOAL`은 선택 |
| B10 | 룸 이름 "쌌쌌맞은" / "쌀쌀맞은" | 시드는 "쌀쌀맞은" |
| B11 | 리프레시 토큰 유효기간이 명세에 없음 | 14일, 설정값으로 둠 |
| B12 | 쿠키 `SameSite` (프론트와 백엔드가 같은 사이트인지 모름) | 설정값으로 두고 기본 `Lax`. 다른 도메인이면 `None; Secure` |
| B13 | 에러 응답 형식이 명세에 없음 | `{ "code": "SESSION_ENDED", "message": "...", "detail": { ... } }`. 409 `SESSION_ALREADY_IN_PROGRESS`는 `detail.sessionId` |
| B14 | Spotless 포맷터 | `palantirJavaFormat` |

---

## 1. 모듈 지도

### 테이블 → 모듈
| 모듈 (담당) | 테이블 | schema.sql 섹션 |
|---|---|---|
| user (성욱) | users, user_social_accounts, terms, term_versions, user_terms, user_memories | 1, 2, 6(일부) |
| content (성욱) | live2d_models, live2d_motions, live2d_expressions, live2d_reaction_maps, avatars, study_rooms, room_backgrounds, characters, character_contents | 3, 4, 5, 6(일부) |
| session (성욱) | study_sessions, session_characters, session_events | 7 |
| relation (승희) | user_character_relations, relation_logs, user_unlocked_contents, user_unlocked_backgrounds | 5·6·7에 흩어져 있음 |
| dialogue (승희) | conversations, chat_messages, llm_usages | 8 |

`zones.conf` 수정 제안: 없음. (`user_unlocked_backgrounds`는 schema 섹션 5, `relation_logs`는 섹션 7에 있지만 HARNESS.md 제안안대로 relation에 둔다.)

### 엔드포인트 → 모듈
| 모듈 | API (api.md 순서) |
|---|---|
| user | 1 약관, 2·3 이메일 인증, 4 가입, 5 로그인, 6 카카오, 7 갱신, 24 로그아웃, 8 내 정보, 10 프로필 설정, 13·14 일정 |
| content | 9 아바타 목록 |
| session | 11 홈, **12 룸 상세**(0장 2번), 16 공부 시작, 17 이어하기, 18 상태 변화, 21 heartbeat, 22 종료, 23 리포트 |
| dialogue | 19 이벤트 반응, 20 메시지 |
| relation | 직접 여는 API 없음 (session·dialogue가 `api`로 부름) |

공용(`common`, 성욱·승희 공동): 보안 설정(JWT 검증), 현재 사용자 id 주입, 에러 응답 형식, `Clock` 빈(한국 시간대), 요청 횟수 제한 도우미(Redis). `@ApplicationModule(type = OPEN)`.

### 마이그레이션 (FK 순서, `scripts/new-migration.sh`로 생성)
| 순서 | 파일 (모듈_설명) | 내용 |
|---|---|---|
| 1 | `user_init` | users(아바타 FK 없이), user_social_accounts, terms, term_versions, user_terms, user_memories |
| 2 | `content_init` | live2d 4종, avatars, study_rooms, room_backgrounds, characters, character_contents |
| 3 | `user_avatar_fk` | `ALTER TABLE users ADD FOREIGN KEY (avatar_id) REFERENCES avatars` |
| 4 | `session_init` | study_sessions, session_characters, session_events |
| 5 | `relation_init` | user_character_relations, user_unlocked_contents, user_unlocked_backgrounds, relation_logs |
| 6 | `dialogue_init` | conversations, chat_messages, llm_usages |
| 7 | `content_seed_demo` | A6 시드 (룸 1, 채린, 기본 아바타, 모델·모션·표정·반응 매핑) |
| 8 | `user_seed_terms` | 약관 SERVICE·PRIVACY v1 |

- schema.sql의 `BEGIN;`/`COMMIT;`은 뺀다 (Flyway가 마이그레이션마다 트랜잭션을 건다).
- A1·A2가 확정되면 노션 → spec-sync → 해당 `*_init`에 반영(아직 develop에 안 들어갔으므로 이 PR 안에서는 고쳐도 됨).

---

## 2. 엔드포인트별 구현 수준

| # | API | 모듈 | 수준 | 이유 |
|---|---|---|---|---|
| 1 | GET /api/terms | user | 단순 | 약관별 최신 시행 버전 조회 |
| 2 | POST /auth/email/verification | user | 단순 | 6자리·5분·1분 1회·409 규칙이 다 있음. 발송은 `EmailSender` Fake |
| 3 | POST .../verification/confirm | user | 단순 | 5회 틀리면 폐기, 가입 토큰 10분 |
| 4 | POST /auth/email/signup | user | 단순 | bcrypt, 필수 약관 검사, `user_terms` 기록. **A1 필요** |
| 5 | POST /auth/email/login | user | 단순 | 5회 실패 시 5분 잠금 |
| 6 | POST /auth/social/KAKAO | user | 단순 | 서버 절차가 명세에 있음. 카카오 호출은 `KakaoClient` Fake. **A1 필요** |
| 7 | POST /auth/refresh | user | 단순 | 리프레시 교체 |
| 8 | GET /api/me | user | 단순 | `ActiveSessionLookup`으로 진행 중 세션 |
| 9 | GET /api/avatars | content | 단순 | `modelJsonUrl` = CDN 주소 설정 + `storage_prefix` + `model_json_path` |
| 10 | PATCH /api/me | user | 단순 | 아바타 없으면 기본(정렬 첫 번째) |
| 11 | GET /api/home | session | 단순 | 진행 중 세션 + 활성 룸·캐릭터 |
| 12 | GET /api/rooms/{roomId} | session | 단순 + 판단 1 | 조회는 단순. `relation.levelName`이 판단(J-03) |
| 13 | POST /api/me/memories | user | 단순 | 검증 규칙이 다 있음, dDay는 한국 날짜 |
| 14 | GET /api/me/memories | user | 단순 | 범위·탭·정렬 규칙이 다 있음 |
| 16 | POST /api/sessions | session | 단순 | 세션 + 세션 캐릭터 + 처음 만나면 관계 생성, 기본 배경 |
| 17 | POST .../resume | session | 단순 | BREAK·FOCUS 삽입 규칙이 다 있음 |
| 18 | POST .../events | session | 단순 + 판단 1 | 저장·중복 거르기·휴식 중 무시는 단순. **호감도 값이 "예시, 기획 확정 필요"**(J-01) |
| 19 | POST .../events/{eventId}/reaction | dialogue | 판단 | trigger 결정과 반응 매핑 선택은 단순. 대사(LLM)·고정 대사·TTS가 판단(J-04, J-10) |
| 20 | POST .../messages | dialogue | 판단 | 저장·중복·속도 제한은 단순. 답장·감정·호감도 판정이 LLM(J-05~07, J-10). **A3 필요** |
| 21 | POST .../heartbeat | session | 단순 | `last_heartbeat_at` 갱신 |
| 22 | POST .../end | session | 단순 + 판단 4 | 검증·저장·멱등·`return_count`는 단순. STUDY_TIME 공식(J-02), 레벨 기준(J-03), 요약(J-08), 완료 대사(J-09), 서버 계산값과 "크게" 다를 때(J-11) |
| 23 | GET .../report | session | 단순 | 저장된 결과 다시 조립. **A2 필요** |
| 24 | POST /auth/logout | user | 단순 | 리프레시 삭제, 쿠키 비움 |
| - | 자동 마감 배치 | session | 단순 | 6시간·`last_heartbeat_at` 기준이 있음. 이벤트로 시간을 계산하는 로직은 22와 공용 |

---

## 3. API ↔ DB 불일치

0장 A·B에 질문으로 올렸다. 요약하면 다음과 같다.
- DB에 자리가 없음: `closingLine`(A2, 컬럼 추가로 해결), 약관 `contentUrl`(A5, 본문 응답으로 해결)
- DB 제약과 흐름이 충돌: `users.nickname` NOT NULL(A1, 가입 때 받는 것으로 해결)
- 응답에 필요한데 명세가 비어 있음: 레벨 이름·다음 레벨 포인트(J-03), 이벤트 반응 고정 대사(J-04)
- 명세 안에서 서로 다름: A3, A4, B2~B4, B9
- 참고(고치지 않음): `live2d_reaction_maps.trigger_type` 주석에 `GAZE_AWAY`가 남아 있음. 서버 enum은 명세 19번의 trigger(`GREETING`·`AWAY`·`RETURN`·`BREAK`·`BREAK_END`)로 둔다.

---

## 4. 계약 목록 (각 모듈 `api` 패키지)

| 계약 | 제공 | 쓰는 쪽 | 내용 |
|---|---|---|---|
| `AvatarQuery` | content | user, session | 아바타 존재·기본 아바타 id·썸네일·모델 |
| `RoomQuery` | content | session | 활성 룸 목록(홈), 룸 상세, 기본 배경 id, 배경 이미지 |
| `CharacterQuery` | content | session, dialogue | 캐릭터 정보·모델·성격, 그 룸 캐릭터인지 |
| `ReactionQuery` | content | dialogue | (캐릭터, trigger, intensity)로 모션·표정 하나 고르기 (같은 확률) |
| `UserQuery` | user | session, dialogue | 닉네임·아바타 id·온보딩 여부 |
| `MemoryQuery` | user | dialogue | 일정 목록 (LLM 맥락용) |
| `ActiveSessionLookup` (인터페이스만) | user | session이 구현 | 진행 중 세션 요약 (`/api/me`용) |
| `RelationQuery` | relation | session, dialogue | 관계 보기(레벨·이름·포인트·함께한 시간, 안 만났으면 Lv.1·0), 이벤트별 변화량 |
| `RelationCommands` | relation | session, dialogue | 처음 만남 기록, 상태 이벤트 반영, 대화 점수 반영, 공부 시간 정산 → 전후 값 |
| `SessionQuery` | session | dialogue | 세션 소유·진행 여부·캐릭터, 이벤트 정보(타입·세션 첫 FOCUS인지·휴식 뒤인지) |
| `SessionClosingHandler` (인터페이스만) | session | dialogue가 구현 | 종료 시 요약 합치기·메시지 삭제·완료 대사 |
| `DialogueQuery` | dialogue | session | 리포트용 저장된 완료 대사 (A2 확정 후) |

DTO는 `record`, 엔티티는 밖으로 내보내지 않는다. 각 `api` 패키지에 `@NamedInterface("api")`.

---

## 5. 전체 플로우 E2E (`docs/specs/flow.md` 초안)

와이어프레임 H01 → H10 순서다. `FlowE2ETest`(MockMvc + Testcontainers)와 `http/flow.http`가 이 순서를 그대로 따른다.

### 흐름 1: 이메일 가입부터 리포트까지
1. 약관 조회 `GET /api/terms` → 약관 2개, `termVersionId`
2. 인증번호 요청 `POST /api/auth/email/verification` → 202 (Fake 메일이 번호를 들고 있음)
3. 인증 확인 `POST .../verification/confirm` → `verificationToken`
4. 가입 `POST /api/auth/email/signup` (닉네임 포함) → 201, `onboardingRequired: true`, 리프레시 쿠키
5. 아바타 목록 `GET /api/avatars` → 1개
6. 프로필 설정 `PATCH /api/me` → `onboardingRequired: false`
7. 홈 `GET /api/home` → 룸 1개, `activeSession: null`
8. 룸 상세 `GET /api/rooms/1` → 채린, Lv.1 · 0초
9. 일정 저장 `POST /api/me/memories` (EXAM) → 201, `dDay`
10. 공부 시작 `POST /api/sessions` → 201, `sessionId`
11. `FOCUS` 전송 → `eventId` → 반응 → `trigger: GREETING`, Fake 대사, 호감도 +5 (FIRST_MEET, J-01 Fake)
12. heartbeat → 204
13. `AWAY` 전송 → 반응(`intensity: 1`) → `trigger: AWAY`, 호감도 −1
14. 같은 `AWAY`를 다시 보냄 → 200 + 같은 `eventId` (재전송 안전)
15. `RETURN` 전송 → 반응 → 호감도 +1
16. 메시지 전송 → Fake 답장, 같은 `requestId` 재전송 시 한 번만 처리
17. 내 정보 `GET /api/me` → `activeSession` 있음 (H18 근거)
18. 공부 종료 `POST .../end` → `returnCount: 1`, `pointsAfter > pointsBefore`, Fake `closingLine`
19. 종료 다시 호출 → 200 + 같은 결과 (멱등)
20. 리포트 `GET .../report` → 18과 같은 결과
21. 토큰 갱신 `POST /api/auth/refresh` → 새 토큰, 옛 리프레시는 401
22. 로그아웃 → 204

### 흐름 2: 카카오 신규 가입 (Fake 카카오)
카카오 로그인(약관 없음) → 400 `TERMS_REQUIRED` → 약관 포함 → `isNewUser: true`, 닉네임 = Fake 카카오가 준 닉네임 → 같은 카카오 계정으로 다시 → `isNewUser: false`

### 흐름 3: 비정상 종료 후 이어하기
공부 시작 → FOCUS → heartbeat → (끊김) → 이어하기 → `breakSeconds > 0`, 끊긴 구간이 공부 시간에 들어가지 않음 → 종료

`ModularityTests`(`ApplicationModules.of(CapsuleApplication.class).verify()`)도 같이 둔다.

---

## 6. 판단 백로그 후보 (확인 후 `judgment-backlog.md`로)

| ID | 모듈 | 담당 | 무엇이 미정인가 | Fake 동작 | 근거 |
|---|---|---|---|---|---|
| J-01 | relation | seunghee | 상태 이벤트별 호감도 변화량 | 명세 예시값: 첫 만남 첫 FOCUS +5, AWAY −1, RETURN +1, BREAK 0 | api.md 18 "예시, 기획 확정 필요" |
| J-02 | relation | seunghee | 공부 시간 → STUDY_TIME 점수 공식 | 공부 1분당 +1 | api.md 22 |
| J-03 | relation | seunghee | 레벨 기준 포인트·레벨 이름 | 0점 Lv.1 '아는 사이', 100점 Lv.2 '친한 사이', 300점 Lv.3 (명세 예시와 맞춤) | api.md 12, 22 |
| J-04 | dialogue | seunghee | 이벤트 반응 대사(LLM) + trigger별 고정 대사(5초 초과) | trigger별 고정 문장 1개 | api.md 19 |
| J-05 | dialogue | seunghee | 대화 답장 생성 (캐릭터 설정·요약·대화·일정) | 고정 문장 + 입력 일부 | api.md 20 |
| J-06 | dialogue | seunghee | 대화 호감도 판정 (−5~5) | 항상 +1 | api.md 20, DB relation_logs |
| J-07 | dialogue | seunghee | 답장 감정 → 모션·표정 고르는 규칙 | 모션·표정 없음(null) | api.md 20 |
| J-08 | dialogue | seunghee | 세션 종료 시 대화 요약 합치기 | 기존 요약 + "메시지 n개" 한 줄 | api.md 22, DB conversations |
| J-09 | dialogue | seunghee | 완료 대사 `closingLine` 생성 | 캐릭터 이름을 넣은 고정 문구 | api.md 22·23 (A2) |
| J-10 | dialogue | seunghee | TTS `audioBase64` | 항상 null | api.md 19·20 |
| J-11 | session | seonguk | 종료 시 프론트 값과 서버 계산값이 "크게" 다를 때의 기준 | 서버 계산은 하되 명세의 부등식 검증만 하고 프론트 값 사용 | api.md 22 "같이 챙길 것" |

뼈대 범위 밖 (API가 없어서 이번엔 안 함): 배경·콘텐츠 해금 규칙(DB 주석 "미정"), 구매 해금(BM 확정 후), 실제 카카오·메일 어댑터(키 필요), 실제 LLM 어댑터(dialogue 담당).

---

## 7. 확인 후 진행 순서
1. (확정 시) ADR 0011 인증 추가
2. Gradle 기반 (`references/build-setup.md`) → 마이그레이션 → 각 모듈 `api` 계약 → 단순 구현 + 테스트 → 판단 Fake + 백로그 → dialogue LLM 자리 → `FlowE2ETest`·`http/flow.http`·`ModularityTests` → `scripts/verify.sh --full` 통과
3. 커밋은 단계별로 나눠서 PR 하나 (`[skeleton] ...`)
