<!-- 명세 원본 (ADR 0013). 고칠 때는 이 파일만 바꾸는 작은 명세 PR → 머지 뒤 spec-sync로 노션 사본에 반영. 노션 위치: docs/specs/SOURCE.md -->
# API 명세 (시연 API 최종본)

> 명세 원본. 2026-11-04 전까지는 노션이 원본이었고 이 파일은 그 사본이었다(spec-sync 7차까지). 노션은 이제 이 파일의 읽기 전용 사본이다.

| 순서 | 분류 | 메서드 | 엔드포인트 | 이름 |
|---|---|---|---|---|
| 1 | 인증 | GET | `/api/terms` | 약관 조회 |
| 2 | 인증 | POST | `/api/auth/email/verification` | 이메일 인증번호 요청 |
| 3 | 인증 | POST | `/api/auth/email/verification/confirm` | 이메일 인증 확인 |
| 4 | 인증 | POST | `/api/auth/email/signup` | 회원가입 |
| 5 | 인증 | POST | `/api/auth/email/login` | 이메일 로그인 |
| 6 | 인증 | POST | `/api/auth/social/KAKAO` | 카카오 로그인 · 가입 |
| 7 | 인증 | POST | `/api/auth/refresh` | 토큰 갱신 |
| 8 | 온보딩 · 홈 | GET | `/api/me` | 내 정보 조회 |
| 9 | 온보딩 · 홈 | GET | `/api/avatars` | 아바타 목록 조회 |
| 10 | 온보딩 · 홈 | PATCH | `/api/me` | 프로필 설정 |
| 11 | 온보딩 · 홈 | GET | `/api/home` | 홈 조회 |
| 12 | 온보딩 · 홈 | GET | `/api/rooms/{roomId}` | 스터디룸 상세 조회 |
| 13 | 온보딩 · 홈 | POST | `/api/me/memories` | 일정 저장 |
| 14 | 온보딩 · 홈 | GET | `/api/me/memories` | 내 일정 조회 |
| 16 | 공부 세션 | POST | `/api/sessions` | 공부 시작 |
| 17 | 공부 세션 | POST | `/api/sessions/{sessionId}/resume` | 세션 이어하기 |
| 18 | 공부 세션 | POST | `/api/sessions/{sessionId}/events` | 공부 상태 기록 |
| 19 | 대화 | POST | `/api/sessions/{sessionId}/events/{eventId}/reaction` | 상태 반응 받기 |
| 20 | 대화 | POST | `/api/sessions/{sessionId}/messages` | 메시지 전송 |
| 21 | 공부 세션 | POST | `/api/sessions/{sessionId}/heartbeat` | heartbeat |
| 22 | 리포트 | POST | `/api/sessions/{sessionId}/end` | 공부 종료 |
| 23 | 리포트 | GET | `/api/sessions/{sessionId}/report` | 리포트 조회 |
| 24 | 인증 | POST | `/api/auth/logout` | 로그아웃 |
| 25 | 인증 | POST | `/api/auth/password/reset-request` | 비밀번호 재설정 메일 요청 |
| 26 | 인증 | POST | `/api/auth/password/reset` | 비밀번호 재설정 |
| 27 | 인증 | DELETE | `/api/me` | 회원 탈퇴 |

순서 15는 노션에 없음 (번호 비어 있음).

## 공통 규칙 (모든 API)
- 노션: https://app.notion.com/p/40cb9a07e5928153a1a9cc534c62d0b7 (상위 페이지의 '공통 규칙' 섹션)

- 인증: `Authorization: Bearer <accessToken>`. '인증: 불필요' API는 이 헤더를 보지 않음
- 시각(`startedAt` 등)은 UTC ISO-8601 (예: `2026-11-07T05:00:02Z`). 날짜 · 시간(`dueDate` · `dueTime`)은 한국 날짜 · 시간 그대로
- 에러 응답 모양 (2026-11-03 결정). `code`로 분기하고, `detail`은 보통 `{}` (409 SESSION_ALREADY_IN_PROGRESS는 `detail.sessionId`)

```json
{ "code": "SESSION_ENDED", "message": "SESSION_ENDED", "detail": {} }
```

| 상태 | code | 언제 |
| --- | --- | --- |
| 400 | INVALID_REQUEST | JSON 형식이나 타입이 틀림 (예: 숫자 자리에 `"abc"`. 숫자 모양 문자열 `"50"`은 숫자로 받음). 각 API의 검증 코드보다 먼저 걸림 |
| 401 | UNAUTHORIZED | 토큰 없이 인증 필요 API 호출 |
| 401 | AUTH_TOKEN_EXPIRED | 토큰이 만료됐거나 잘못됨 → '토큰 갱신' 후 다시 요청 |
| 404 | NOT_FOUND | 없는 경로 (토큰이 있을 때. 토큰 없이 부르면 401 UNAUTHORIZED가 먼저) |
| 405 | METHOD_NOT_ALLOWED | 경로는 있지만 메서드가 틀림 (404와 같이 토큰이 있을 때) |
| 500 | INTERNAL_ERROR | 서버 오류 |

## 1. GET /api/terms — 약관 조회
- 분류: 인증
- 설명: 회원가입 화면에 띄울 필수 약관 2종 최신 버전
- 와이어프레임: H01 회원가입 약관 체크
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/abcb9a07e59282988185012651220aa9

#### 설명

회원가입 화면에 띄울 약관의 최신 버전을 가져옵니다. 가입할 때 여기서 받은 `termVersionId`로 동의를 보냅니다.

#### 와이어프레임 대조

| 화면 요소 | 이 API가 하는 일 |
| --- | --- |
| H01 회원가입 '[필수] 이용약관' · '[필수] 개인정보 처리방침' 체크 | 약관 이름 · 필수 여부 · 버전 id |
| H01 '전문 보기' | `content` (본문을 그대로 표시) |

- 테이블: `terms`, `term_versions` · 인증: 불필요

#### [GET] /api/terms

#### 🛬 Response

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| terms[].termVersionId | Long | 동의할 때 보낼 id |
| terms[].code | String | SERVICE / PRIVACY |
| terms[].name | String | 화면 표시 이름 |
| terms[].required | Boolean | 필수 여부 |
| terms[].content | String | 약관 전문 (`term_versions.content`). 별도 링크 없음 |

```json
{
  "terms": [
    { "termVersionId": 3, "code": "SERVICE", "name": "이용약관", "required": true, "content": "제1조 (목적) ..." },
    { "termVersionId": 4, "code": "PRIVACY", "name": "개인정보 처리방침", "required": true, "content": "1. 수집하는 개인정보 ..." }
  ]
}
```

> ✅ **200 OK**

## 2. POST /api/auth/email/verification — 이메일 인증번호 요청
- 분류: 인증
- 설명: 가입할 이메일로 6자리 인증번호 발송 (5분 유효)
- 와이어프레임: H01 회원가입 이메일 (인증 칸 추가 필요)
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/9e7b9a07e5928202a0a681439fa8dd19

#### 설명

가입할 이메일로 6자리 인증번호를 보냅니다. DB 설계가 '인증을 먼저 하고 가입'이라 가입 전에 거칩니다. 인증번호는 Redis에 5분 보관합니다.

#### 와이어프레임 대조

| 화면 요소 | 이 API가 하는 일 |
| --- | --- |
| H01 회원가입 '이메일' 입력 | 이 이메일로 인증번호 발송 |

> ⚠️ 현재 와이어프레임 회원가입 화면에는 **'인증번호 보내기' 버튼과 인증번호 입력 칸이 없음**. DB 설계(인증 먼저 → 가입)를 따르려면 이메일 아래에 추가 필요

- 저장: Redis · 인증: 불필요

#### [POST] /api/auth/email/verification

#### 🛫 Request

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| email | String | O | 가입할 이메일 (서버가 소문자로 바꿈) |

```json
{ "email": "name@example.com" }
```

#### 🛬 Response

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| expiresInSeconds | Integer | 인증번호 유효시간 (300) |

> ✅ **202 Accepted**

> ❌ **400** INVALID_EMAIL · **409** EMAIL_ALREADY_USED · **429** TOO_MANY_REQUESTS (1분에 1번)

## 3. POST /api/auth/email/verification/confirm — 이메일 인증 확인
- 분류: 인증
- 설명: 인증번호 확인 → 가입용 토큰 발급
- 와이어프레임: H01 회원가입 (인증번호 칸 추가 필요)
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/64eb9a07e59283f496a581e09d92a08e

#### 설명

입력한 인증번호를 확인하고, 가입에 쓸 `verificationToken`(10분)을 돌려줍니다.

#### 와이어프레임 대조

| 화면 요소 | 이 API가 하는 일 |
| --- | --- |
| (추가 필요) 인증번호 입력 칸 | 번호 확인 → 가입 토큰 |

- 저장: Redis · 인증: 불필요

#### [POST] /api/auth/email/verification/confirm

#### 🛫 Request

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| email | String | O | 인증번호를 받은 이메일 |
| code | String | O | 6자리 |

```json
{ "email": "name@example.com", "code": "483920" }
```

#### 🛬 Response

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| verificationToken | String | 가입 요청에 넣을 토큰 (10분) |

> ✅ **200 OK**

> ❌ **400** INVALID_CODE · CODE_EXPIRED · **429** TOO_MANY_ATTEMPTS (5회 틀리면 번호 폐기)

## 4. POST /api/auth/email/signup — 회원가입
- 분류: 인증
- 설명: 이메일 · 비밀번호 · 닉네임 · 약관 동의로 가입, 바로 로그인 토큰 발급
- 와이어프레임: H01 회원가입 폼
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/2dbb9a07e592820aa64601d3e4aa71aa

#### 설명

이메일 · 비밀번호 · 닉네임 · 약관 동의로 가입하고 바로 로그인 토큰을 발급합니다. 닉네임은 가입할 때 받습니다.

#### 와이어프레임 대조

| 화면 요소 | 이 API가 하는 일 |
| --- | --- |
| H01 '이메일' | `verificationToken` 안에 들어 있음 |
| H01 '비밀번호' (8자 이상) | `password` |
| H01 '비밀번호 확인' | 프론트에서만 일치 검사, 서버로 안 보냄 |
| H01 '닉네임' (추가 필요) | `nickname` |
| H01 [필수] 약관 2개 | `agreedTermVersionIds` |
| H01 '회원가입' 버튼 | 가입 → H02로 |

- 테이블: `users` (email, bcrypt 해시, nickname), `user_terms` · 인증: 불필요

#### [POST] /api/auth/email/signup

#### 🛫 Request

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| verificationToken | String | O | 이메일 인증 확인에서 받은 토큰 |
| password | String | O | 8자 이상, 72바이트 이하 (bcrypt 한계. 영문·숫자 72자, 한글 약 24자). 벗어나면 INVALID_PASSWORD |
| nickname | String | O | 1~20자 (프로필 설정과 같은 규칙) |
| agreedTermVersionIds | Array | O | 필수 약관이 빠지면 가입 불가 |

```json
{ "verificationToken": "vt_9f2...", "password": "capsule1234", "nickname": "승희", "agreedTermVersionIds": [3, 4] }
```

#### 🛬 Response

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| accessToken | String | 액세스 토큰 (30분). 리프레시 토큰은 HttpOnly 쿠키 |
| userId | Long | 사용자 id |
| onboardingRequired | Boolean | 가입 직후는 항상 true → H02 |

```json
{ "accessToken": "eyJ...", "userId": 7, "onboardingRequired": true }
```

> ✅ **201 Created**

> ❌ **400** INVALID_PASSWORD · INVALID_NICKNAME · TERMS_REQUIRED · VERIFICATION_EXPIRED · **409** EMAIL_ALREADY_USED

> ⚠️ 닉네임은 가입할 때 받기로 함 (2026-11-03 결정, DB `users.nickname` NOT NULL 유지). 현재 H01 회원가입 화면에 닉네임 칸 추가 필요

## 5. POST /api/auth/email/login — 이메일 로그인
- 분류: 인증
- 설명: 이메일 · 비밀번호 로그인
- 와이어프레임: H01 로그인 폼
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/d28b9a07e59282de843a0113489d74c2

#### 설명

이메일 · 비밀번호로 로그인합니다. 카카오는 '카카오 로그인 · 가입' API를 따로 씁니다.

#### 와이어프레임 대조

| 화면 요소 | 이 API가 하는 일 |
| --- | --- |
| H01 '이메일' · '비밀번호' · '로그인' | 로그인 |
| H01 '카카오로 계속하기' | '카카오 로그인 · 가입' API |
| H01 '가입 없이 둘러보기' | 시연 제외 (목업) |

- 테이블: `users` · 저장: 리프레시 토큰은 Redis · 인증: 불필요

#### [POST] /api/auth/email/login

#### 🛫 Request

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| email | String | O | 가입한 이메일 |
| password | String | O | 비밀번호 |

```json
{ "email": "name@example.com", "password": "capsule1234" }
```

#### 🛬 Response

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| accessToken | String | 액세스 토큰 (30분). 리프레시 토큰은 HttpOnly 쿠키 |
| userId | Long | 사용자 id |
| onboardingRequired | Boolean | 아바타 설정 전이면 true → H02, 아니면 H03 |

```json
{ "accessToken": "eyJ...", "userId": 7, "onboardingRequired": false }
```

> ✅ **200 OK**

> ❌ **401** INVALID_CREDENTIALS (무엇이 틀렸는지는 알려주지 않음) · **429** LOGIN_LOCKED (5회 실패 시 5분 잠금)

## 6. POST /api/auth/social/KAKAO — 카카오 로그인 · 가입
- 분류: 인증
- 설명: 카카오 인가 코드로 로그인, 처음이면 가입까지 (소셜은 카카오만)
- 와이어프레임: H01 카카오로 계속하기
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/eafb9a07e59282c19d5d012f95549d8a

#### 설명

카카오 인가 코드로 로그인합니다. 처음 온 사용자면 같은 요청에서 가입까지 끝내고, 가입 직후엔 H02(아바타)로 보냅니다. 닉네임은 카카오 닉네임을 쓰고 H14 프로필에서 바꿀 수 있습니다. 소셜 로그인은 카카오만 지원합니다.

#### 와이어프레임 대조

| 화면 요소 | 이 API가 하는 일 |
| --- | --- |
| H01 '카카오로 계속하기' | 카카오 로그인 화면으로 이동 → 돌아온 인가 코드로 이 API 호출 |
| 로그인 후 분기 | `onboardingRequired`면 H02, 아니면 H03 |

> ⚠️ 카카오 가입은 약관 화면 · 체크 · 안내 문구가 없음. 카카오로 가입하면 최신 필수 약관에 동의한 것으로 보고 서버가 기록함. 정식 출시 전에는 약관 고지 방식(안내 문구 등)을 다시 정해야 함 (확정 필요)

> 2026-11-05: 카카오 로그인 요청에서 약관 동의(`agreedTermVersionIds`, 400 TERMS_REQUIRED) 제거. 가입하면 최신 필수 약관 동의로 기록

- 테이블: `users`(이메일 · 비밀번호 NULL), `user_social_accounts`(provider · provider_user_id), `user_terms` · 인증: 불필요

#### [POST] /api/auth/social/KAKAO

#### 🛫 Request

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| code | String | O | 카카오가 리다이렉트로 준 인가 코드 |
| redirectUri | String | O | 카카오 앱에 등록한 리다이렉트 주소 |

```json
{ "code": "Kx8...", "redirectUri": "https://capsule.app/oauth/kakao" }
```

#### 🛬 Response

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| accessToken | String | 액세스 토큰 (30분). 리프레시 토큰은 HttpOnly 쿠키 |
| userId | Long | 사용자 id |
| isNewUser | Boolean | 이번에 가입했는지 |
| onboardingRequired | Boolean | 아바타 설정 전이면 true → H02 |

```json
{ "accessToken": "eyJ...", "userId": 8, "isNewUser": true, "onboardingRequired": true }
```

> ✅ **200 OK**

> ❌ **401** SOCIAL_AUTH_FAILED (인가 코드 만료 · 오류)

#### 서버가 하는 일

1. 인가 코드를 카카오에 보내 카카오 회원번호와 닉네임을 받음
2. `user_social_accounts`에서 (KAKAO, 회원번호) 조회
3. 있으면 그 사용자로 로그인, 없으면 `users` + `user_social_accounts` + `user_terms`를 한 트랜잭션으로 생성. `user_terms`에는 가입 시점의 최신 필수 약관 버전을 동의로 기록. 닉네임은 카카오 프로필 닉네임으로 저장 (DB `users.nickname` NOT NULL, 프로필 설정에서 변경 가능). 카카오 닉네임이 비어 있으면 '카카오 사용자', 20자를 넘으면 앞 20자만 저장 (가입을 막지 않음)
4. 이메일이 같아도 이메일 가입 계정과 자동으로 합치지 않음 (다른 계정으로 취급)

#### 프론트 참고

- 카카오 로그인 전후로 약관 관련 화면 · 문구를 두지 않음 (약관 체크는 이메일 회원가입에만)
- **시연은 공용 기기**라, 로그아웃할 때 카카오 계정 로그아웃까지 같이 해야 다음 체험자가 이전 사람 계정으로 자동 로그인되지 않음
- 카카오 디벨로퍼스에 시연 도메인의 리다이렉트 URI를 미리 등록해야 함
- 카카오 디벨로퍼스 동의 항목에서 '닉네임'(profile_nickname)을 **필수 동의**로 설정해야 함 (가입 닉네임으로 씀)

## 7. POST /api/auth/refresh — 토큰 갱신
- 분류: 인증
- 설명: 액세스 토큰 만료 시 재발급
- 와이어프레임: 공통
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/99db9a07e59283bc909301b2834f24aa

#### 설명

액세스 토큰(30분)이 만료되면 리프레시 토큰 쿠키로 새 토큰을 받습니다. 리프레시 토큰은 쓸 때마다 교체됩니다.

#### 와이어프레임 대조

화면 요소 없음 (공통). 특히 H07 공부 중(50분 이상)에도 토큰이 만료되므로 필수.

- 저장: Redis · 인증: 리프레시 토큰 쿠키

#### [POST] /api/auth/refresh

#### 🛬 Response

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| accessToken | String | 새 액세스 토큰 |

> ✅ **200 OK**

> ❌ **401** REFRESH_TOKEN_INVALID → 로그인 화면으로

#### 프론트 참고

- 어떤 API든 401 `AUTH_TOKEN_EXPIRED`가 오면 이 API를 한 번 부르고 원래 요청을 다시 보냄
- 리프레시 토큰 쿠키: 이름 `refresh_token`, `HttpOnly`, `Path=/api/auth`, `SameSite=Lax`, 배포 환경은 `Secure`. 요청은 쿠키를 같이 보냄 (`credentials: 'include'`)
- 쓸 때마다 교체되므로 여러 탭이 동시에 갱신하면 하나만 성공하고 나머지는 401. 갱신 요청은 하나로 묶어서 보냄
- 이 API는 Authorization 헤더를 보지 않음 (만료된 액세스 토큰이 붙어 있어도 됨)

## 8. GET /api/me — 내 정보 조회
- 분류: 온보딩 · 홈
- 설명: 내 정보 + 온보딩 필요 여부 + 진행 중 세션(H18 판단)
- 와이어프레임: 로그인 직후 분기 · H03 헤더 · H18
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/b63b9a07e5928365a77c8137046b0b34

#### 설명

##### 그럼 `GET /me` 같은 API는 원래 왜 만드나

보통은 이런 상황 때문임.

- 화면 대부분에 **공통 헤더**(닉네임 · 프로필 · 알림 · 크레딧)가 있어서, 앱이 켜질 때 한 번 받아 전역으로 들고 있으려고
- 권한 · 역할(관리자 등)에 따라 메뉴가 달라서

> 2026-11-03: 남기기로 함. 새로고침 때 어느 화면으로 보낼지 정하는 기준

항상 호출되는 API. 비정상적인 종료의 대비

한 줄로 말하면 **지금 로그인한 사람이 누구고, 어느 화면으로 보내야 하는지"를 알려주는 API**

로그인 직후 · 새로고침 때 부릅니다. 어느 화면으로 보낼지 정하는 기준입니다.

- `onboardingRequired = true` → H02 아바타 만들기
- `activeSession`이 있음 → H03 위에 **H18 '다시 공부를 시작할까요?'** 팝업
- 둘 다 아니면 → H03 홈

#### 와이어프레임 대조

| 화면 요소 | 이 API가 하는 일 |
| --- | --- |
| H03 헤더 오른쪽 프로필 · 닉네임 | `nickname`, `profileImage` |
| H18 이어하기 팝업 '쌀쌀맞은 갸루짝꿍이…에서 하던 공부' | `activeSession.roomName` |

- 테이블: `users`, `avatars`, `study_sessions` · 인증: 필요

#### [GET] /api/me

#### 🛬 Response

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| userId | Long | 사용자 id |
| nickname | String | 닉네임 (가입 때 받음) |
| email | String | 이메일 |
| onboardingRequired | Boolean | 아바타를 아직 안 골랐으면 true (닉네임은 가입 때 받음) |
| activeSession | Object | 진행 중 세션 `sessionId`, `roomId`, `roomName`, `characterName`, `startedAt`. 없으면 null |
| profileImage | String | 프로필 이미지 = 아바타 썸네일. 아바타 설정 전이면 null |

```json
{
  "userId": 7,
  "nickname": "승희",
  "email": "name@example.com",
  "onboardingRequired": false,
  "activeSession": { "sessionId": 512, "roomId": 1, "roomName": "밤의 호그와트 도서관", "characterName": "말포이", "startedAt": "2026-11-07T05:00:02Z" },
  "profileImage" : "profile_image_url.com"
}
```

> ✅ **200 OK**

> ❌ **401** AUTH_TOKEN_EXPIRED

## 9. GET /api/avatars — 아바타 목록 조회
- 분류: 온보딩 · 홈
- 설명: 완성형 Live2D 아바타 목록 (현재 1개)
- 와이어프레임: H02 아바타 미리보기
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/fecb9a07e592825eacb1010f79c810ff

#### 설명

고를 수 있는 아바타 목록입니다. **파츠 조합(헤어 · 의상 · 외모 · 배경)은 Live2D로 만들기 어려워서, 미리 만든 완성형 아바타 1개**를 내려줍니다.

#### 와이어프레임 대조

| 화면 요소 | 이 API가 하는 일 |
| --- | --- |
| H02 '공부하는 나의 모습 · 아바타 미리보기' | 완성형 아바타 모델 · 썸네일 |
| H02 헤어 · 의상 · 외모 · 배경 탭, '헤어 1~8' | 목업 (API 없음) |
| H02 '카메라를 끄면 이 아바타가 보여요' (문구 변경 필요: 공부 중 내 칸은 영상 대신 이 아바타) | 같은 모델을 H07 내 칸에서 씀 |

> ⚠️ 기획 (2026-11-05): 카메라를 켜도 H07 내 칸에는 영상 대신 아바타를 보여주고, 아바타가 사용자 동작(고개 돌림 · 하품 · 졸음 · 손 흔들기)을 따라 한다. **따라 하기는 프론트가 카메라 감지 결과로 직접** 한다 (서버 API 없음, 18 '규칙' 참고)

- 아바타 모델 조건: 얼굴 트래킹용 표준 파라미터(`ParamAngleX` · `ParamAngleY` · `ParamAngleZ` · `ParamEyeLOpen` · `ParamEyeROpen` · `ParamMouthOpenY`)가 있어야 함. 손을 따라 하려면 팔 · 손목 파라미터가 있는 모델이고 상반신에 손이 보이는 구도여야 함 (예: Live2D 공식 샘플 Haru · Chitose. 2D 모델이라 팔이 움직이는 범위는 모델이 정한 만큼). 없으면 손은 따라 하지 않음 (확정 필요)

> 2026-11-05: 공부 중 내 칸은 카메라를 켜도 아바타, 아바타가 사용자 동작을 따라 함(프론트). 아바타 모델 조건 추가

- 테이블: `avatars` → `live2d_models` · 인증: 필요

#### [GET] /api/avatars

#### 🛬 Response

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| avatars[].id | Long | 아바타 id |
| avatars[].name | String | 이름 |
| avatars[].thumbnailUrl | String | 썸네일 |
| avatars[].model | Object | `modelJsonUrl`, `scale`, `offsetX`, `offsetY` |

```json
{
  "avatars": [
    { "id": 1, "name": "기본 아바타", "thumbnailUrl": "https://cdn.../avatar1.png",
      "model": { "modelJsonUrl": "https://cdn.../live2d/avatar1/v1/avatar1.model3.json", "scale": 1.0, "offsetX": 0, "offsetY": 0 } }
  ]
}
```

> ✅ **200 OK**

## 10. PATCH /api/me — 프로필 설정
- 분류: 온보딩 · 홈
- 설명: 아바타 저장 (온보딩 완료) · 닉네임 · 아바타 변경
- 와이어프레임: H02 이 모습으로 시작하기 · H14 프로필 저장
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/d08b9a07e5928331b54881fec20dc4be

#### 설명

H02에서 아바타를 저장하면 `onboardingRequired`가 false가 되고 H03 홈으로 갑니다. 닉네임은 가입 때 정해져 있어서(이메일은 가입 폼, 카카오는 카카오 닉네임) H02에서 받지 않고, 나중에 H14 프로필에서 바꿉니다. 보낸 값만 바꾸고 안 보낸 값은 그대로 둡니다.

#### 와이어프레임 대조

| 화면 요소 | 이 API가 하는 일 |
| --- | --- |
| H02 '이 모습으로 시작하기' | 완성형 아바타 저장 (`avatarId`) |
| H02 '나중에 바꿀게요' | 빈 요청 `{}` → 기본 아바타(1번)로 저장 |
| H02 '이 모습이 프로필 이미지가 돼요' | 아바타 썸네일을 프로필로 사용 |
| H14 프로필 저장 | 바꾼 것만 보냄 (`nickname` · `avatarId`) |

> ⚠️ H02에 닉네임 입력이 있다면 빼야 함 (닉네임 변경은 H14에서만)

> 2026-11-05: H02에서 닉네임을 받지 않음. `nickname` 필수 → 선택, 안 보낸 값은 유지 (`avatarId`를 안 보내도 기존 아바타 유지, 아바타가 없을 때만 기본)

- 테이블: `users` (`nickname`, `avatar_id`) · 인증: 필요

#### [PATCH] /api/me

#### 🛫 Request

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| nickname | String | X | 1~20자. 없으면 그대로 (보냈는데 비었거나 20자 초과면 INVALID_NICKNAME) |
| avatarId | Long | X | 없으면 그대로. 아직 아바타가 없으면 기본 아바타 |

```json
{ "avatarId": 1 }
```

#### 🛬 Response

'내 정보 조회'와 같은 형태

> ✅ **200 OK**

> ❌ **400** INVALID_NICKNAME · AVATAR_NOT_FOUND

## 11. GET /api/home — 홈 조회
- 분류: 온보딩 · 홈
- 설명: 오늘 공부 시간 · 이어하기 · 들어갈 수 있는 룸 1개
- 와이어프레임: H03 홈
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/f66b9a07e592825d98d1015154dd556d

#### 설명

홈 화면에 필요한 것을 한 번에 가져옵니다.

#### 와이어프레임 대조

| 화면 요소 | 이 API가 하는 일 |
| --- | --- |
| H03 환영 배너 '재방문 시 → 이어서 공부하기' | `activeSession`이 있으면 버튼 표시 |
| H03 '오늘은 어디서 공부할까요?' 포스터 카드 | `rooms` (실제 1개) |
| H03 헤더 크레딧 · 내 기록 · 친구, 저장한 스터디룸 · 검색 | 목업 (API 없음) |

- 테이블: `study_sessions`, `study_rooms`, `characters` · 인증: 필요

#### [GET] /api/home

#### 🛬 Response

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| todayStudySeconds | Integer | 오늘(한국 날짜) 끝낸 세션의 공부 시간 합(초). 진행 중 세션은 빠짐 (2026-11-03 추가: 설명의 '오늘 공부 시간') |
| activeSession | Object | 진행 중 세션 (내 정보 조회와 같은 형태). 없으면 null |
| rooms[] | Array | 들어갈 수 있는 룸 (지금은 1개) |
| rooms[].id · name · thumbnailUrl |  | 포스터 카드 |
| rooms[].characters[] | Array | 포스터 아래 캐릭터 이름 · 프로필 |

```json
{
  "todayStudySeconds": 3300,
  "activeSession": null,
  "rooms": [
    { "id": 1, "name": "밤의 호그와트 도서관", "thumbnailUrl": "https://cdn.../room1.png",
      "characters": [ { "id": 1, "name": "말포이", "profileImageUrl": "https://cdn.../chaerin.png" } ] }
  ]
}
```

> ✅ **200 OK**

## 12. GET /api/rooms/{roomId} — 스터디룸 상세 조회
- 분류: 온보딩 · 홈
- 설명: 룸 정보 + 캐릭터 1명(성격 태그 배열) + 나와의 관계(함께 n분 · Lv)
- 와이어프레임: H04 스터디룸 상세
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/f7eb9a07e59282779f7881792cb5f282

#### 한 줄 요약

스터디룸 상세와 함께할 캐릭터 1명, 그리고 **그 캐릭터와 나의 관계**를 가져옵니다. 캐릭터 성격은 **태그 배열**입니다.

#### 와이어프레임 대조

| 화면 요소 | 응답 필드 |
| --- | --- |
| H04 룸 배너 · 제목 · '[룸 소개 2~3줄]' | `bannerUrl`(가로 배너, 없으면 `thumbnailUrl`), `name`, `description` |
| H04 '이곳에서 함께할 친구들' 카드 이름 · 사진 | `characters[].name`, `profileImageUrl` |
| H04 친구 카드 '#태그' | `characters[].personality` (배열, 순서대로 표시) |
| H04 친구 카드 '◷ 함께 0분 · ♡ Lv.1 아는 사이' | `characters[].relation` |
| H04 '이곳에서 공부하기 →' | H06으로 이동 (API 없음). 캐릭터 모델 정보는 '공부 시작' 응답에 있음 (별도 조회 API 없음) |
| H04 '진행 중인 공부로 돌아가기' | `activeSessionId`가 있으면 버튼 문구를 바꾸고 '세션 이어하기' |
| H04 친구 카드 클릭 → H12, 나머지 캐릭터 | 목업 |

- 테이블: `study_rooms`, `characters`, `user_character_relations` · 인증: 필요

#### [GET] /api/rooms/{roomId}

#### 🛬 Response

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| id · name · description · thumbnailUrl |  | 룸 정보 (`thumbnailUrl`은 홈 포스터 카드와 같은 그림) |
| bannerUrl | String | 룸 상세 가로 배너. 없으면 null (그때 프론트는 `thumbnailUrl`) |
| characters[].id · name · profileImageUrl |  | 캐릭터 (이번 시연은 1명) |
| characters[].personality | Array | 성격 태그 (`characters.personality text[]`) |
| characters[].relation | Object | `level`(함께 공부한 자리 지킴 시간 레벨), `totalStudySeconds`, `points`(호감도 0~100), `stage` · `stageName`(관계 단계 = 호감도 구간), `levelName`(옛 필드, `stageName`과 같은 값, 지울 예정). 아직 안 만났으면 Lv.1 · 0초 · 50 |
| activeSessionId | Long | 이 룸에서 진행 중인 내 세션 id. 없거나 다른 룸에서 진행 중이면 null (그때 공부 시작을 누르면 409 → H18 팝업) |

> 2026-11-05: `bannerUrl` 추가. 홈 포스터 카드(`thumbnailUrl`)와 상세 배너를 다른 그림으로

> 2026-11-04 (호감도 최종안): 레벨과 관계 단계를 나눔. 레벨 = 이 캐릭터와 함께 공부한 자리 지킴 시간(60분당 1, 상한 없음), 관계 단계 = 호감도 구간(0 · 30 · 70 · 90). 단계 이름을 화면에 보일지는 확정 필요 (PM은 숨기기로 함)

```json
{
  "id": 1,
  "name": "밤의 호그와트 도서관",
  "description": "지기 싫어하는 슬리데린 라이벌 말포이와 촛불 아래에서 같이 공부해요.",
  "thumbnailUrl": "https://cdn.../room1.png",
  "bannerUrl": "https://cdn.../room1-banner.webp",
  "characters": [
    { "id": 1, "name": "말포이", "profileImageUrl": "https://cdn.../chaerin.png",
      "personality": ["츤데레", "슬리데린 5학년", "라이벌"],
      "relation": { "level": 1, "levelName": "친한 사이", "totalStudySeconds": 0,
                    "points": 50, "stage": 2, "stageName": "친한 사이" } }
  ],
  "activeSessionId": null
}
```

> ✅ **200 OK**

> ❌ **404** ROOM_NOT_FOUND (목업 룸 id로 호출한 경우)

## 13. POST /api/me/memories — 일정 저장
- 분류: 온보딩 · 홈
- 설명: 시험 · 약속 · 목표 일정 저장
- 와이어프레임: H06 시험 · H10 다음 공부 약속 · H14 일정 추가
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/6cab9a07e59282108b0081090687fbf3

#### 한 줄 요약

시험 · 약속 · 목표 일정을 저장합니다. 날짜와 시간을 **따로** 받습니다 (DB `due_date` · `due_time`). 시간이 없으면 '종일' 일정입니다.

#### 와이어프레임 대조

| 화면 요소 | 이 API가 하는 일 |
| --- | --- |
| H06 '시험이 있나요? · + 일정 추가' | `type = EXAM` |
| H06 '없어요' | 호출 안 함 |
| H10 '다음 공부 약속 · 날짜 · 시간 · 약속 저장하기' | `type = PROMISE` (날짜 + 시간) |
| H10 '저장하지 않고 홈으로' | 호출 안 함 |
| H14 '+ 일정 추가' 다이얼로그 '종류 · 일정 이름 · 날짜 · 시간(약속일 때) · 저장' | `type`, `title`, `dueDate`, `dueTime` |

- 테이블: `user_memories` · 인증: 필요

#### [POST] /api/me/memories

#### 🛫 Request

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| type | String | O | `EXAM`(시험) / `PROMISE`(약속) / `GOAL`(목표) |
| title | String | O | 일정 이름 (약속은 비우면 '같이 공부하기') |
| dueDate | String | EXAM · PROMISE는 O, GOAL은 X | `YYYY-MM-DD` (한국 날짜 그대로). 목표는 마감일이 없을 수 있음 |
| dueTime | String | X | `HH:mm`. 약속에서만 사용 (다른 종류에 오면 버리고 종일로 저장). **dueDate 없이 시간만은 불가** |

```json
{ "type": "PROMISE", "title": "같이 공부하기", "dueDate": "2026-11-08", "dueTime": "20:00" }
```

```json
{ "type": "EXAM", "title": "영어 중간고사", "dueDate": "2026-11-12", "dueTime": null }
```

#### 🛬 Response

'내 일정 조회'의 항목 1개와 같은 형태

```json
{ "memoryId": 32, "type": "PROMISE", "title": "같이 공부하기", "dueDate": "2026-11-08", "dueTime": "20:00", "dDay": 1 }
```

> ✅ **201 Created**

> ❌ **400** INVALID_MEMORY (빈 제목 · 지난 날짜 · 날짜 없이 시간만 · 형식 오류)

#### 참고

- 날짜 · 시간은 타임존 변환 없이 한국 기준 그대로 주고받음 (ISO 시각 아님)
- 저장한 일정은 모든 캐릭터가 기억해서 대화에 활용

## 14. GET /api/me/memories — 내 일정 조회
- 분류: 온보딩 · 홈
- 설명: 내 일정(시험 · 약속 · 목표) 조회 — 달력 점 · 날짜 클릭 · 예정/전체/지난 탭
- 와이어프레임: H14 마이페이지 달력 · 일정 목록
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/da5b9a07e59283ce98c401775309fcb9

#### 한 줄 요약

내 일정(시험 · 약속 · 목표)을 조회합니다. 마이페이지 달력의 점, 날짜 클릭, 예정 · 전체 · 지난 일정 탭을 이 API 하나로 처리합니다.

#### 와이어프레임 대조

| 화면 요소 | 쿼리 · 응답 |
| --- | --- |
| H14 달력 '‹ 0000년 00월 ›' | `from` · `to`로 그 달 범위 |
| H14 달력 '점 = 일정 있음' | `datesWithMemories` |
| H14 '날짜 클릭 → 그 날짜 일정만' | `date` |
| H14 '예정 · 전체 · 지난 일정' 탭 | `status` = `UPCOMING` / `ALL` / `PAST` |
| H14 목록 '시험 · 00.00 · 종일' · '약속 · 00.00 · 00:00' · '목표' | `type`, `title`, `dueDate`, `dueTime`(null이면 '종일') |
| H14 빈 상태 '등록된 일정이 없어요' | `memories: []` |
| H14 '+ 일정 추가' | '일정 저장' API |

- 테이블: `user_memories` · 인증: 필요

#### [GET] /api/me/memories

#### 🛫 Request (Query)

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| from · to | String (`YYYY-MM-DD`) | X | 달력 달의 1일 ~ 말일 |
| date | String (`YYYY-MM-DD`) | X | 특정 날짜만. 있으면 `from` · `to`보다 우선 |
| status | String | X | `UPCOMING`(기본) / `ALL` / `PAST` |
| type | String | X | `EXAM` / `PROMISE` / `GOAL`. 없으면 전부 |

```javascript
GET /api/me/memories?from=2026-11-01&to=2026-11-30&status=UPCOMING
GET /api/me/memories?date=2026-11-12
```

#### 🛬 Response

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| memories[].memoryId | Long | 일정 id |
| memories[].type | String | `EXAM` / `PROMISE` / `GOAL` |
| memories[].title | String | 일정 이름 |
| memories[].dueDate | String | `YYYY-MM-DD`, 없으면 null (마감일 없는 목표) |
| memories[].dueTime | String | `HH:mm`, 없으면 null → '종일' 표시 |
| memories[].dDay | Integer | 오늘 기준 D-day (지난 일정은 음수, 날짜 없으면 null) |
| datesWithMemories | Array | `from`~`to` 안에서 일정이 있는 날짜 (탭 · `date`와 무관. `type`을 보내면 그 종류만) |

```json
{
  "memories": [
    { "memoryId": 32, "type": "PROMISE", "title": "같이 공부하기", "dueDate": "2026-11-08", "dueTime": "20:00", "dDay": 1 },
    { "memoryId": 31, "type": "EXAM", "title": "영어 중간고사", "dueDate": "2026-11-12", "dueTime": null, "dDay": 5 }
  ],
  "datesWithMemories": ["2026-11-08", "2026-11-12"]
}
```

> ✅ **200 OK**

> ❌ **400** INVALID_DATE_RANGE (끝일이 시작일보다 빠름 · 범위 100일 초과)

#### 규칙

- 정렬: 예정 · 전체는 가까운 날짜부터, 지난 일정은 최근 것부터. 날짜 없는 목표는 '전체'의 맨 아래
- 같은 날이면 시간 있는 일정이 먼저, 종일은 그 다음
- 날짜 없는 목표는 '전체'(`status=ALL`)에만 나오고, `from` · `to`와 상관없이 포함됨. `date`를 보내면 빠짐
- `date`와 `status`는 같이 적용됨. 지난 날짜를 누르면 `status=ALL`(또는 `PAST`)을 같이 보냄 (기본값 UPCOMING이면 빈 목록)

## 16. POST /api/sessions — 공부 시작
- 분류: 공부 세션
- 설명: 룸 · 캐릭터 1명 · 목표 시간으로 세션 생성 + 캐릭터 · 내 프로필 이미지
- 와이어프레임: H06 시작하기 → H07
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/515b9a07e59282da86b901b17c7c86fc

#### 한 줄 요약

룸 · 캐릭터 1명 · 목표 시간으로 **세션을 만듭니다**.

세션에 들어갈 캐릭터가 이 시점에 확정되므로, 공부 화면(H07)을 그리는 데 필요한 **캐릭터 · 아바타 정보(이름 · 프로필 이미지)와 Live2D 모델 주소**를 함께 줍니다. 성격 태그는 룸 상세(12)에서만 줍니다 (2026-11-03 정리: 응답 표 기준).

프론트는 이 응답을 받은 뒤 모델 로딩을 시작합니다.

#### 와이어프레임 대조

| 화면 요소 | 이 API가 하는 일 |
| --- | --- |
| H06 '오늘의 목표 시간' 슬라이더 (3분~3시간, 기본 50분. 3 → 5 → 10 → … → 180) | `targetMinutes` 검증 |
| H06 '시작하기' | 세션 생성 → H07 |
| H06 '대화 옵션' 등급 · 보유 크레딧 · 크레딧 부족 안내 | **제거** |
| H07 상단 룸 제목 · 배경 | `room` |
| H07 캐릭터 칸 이름 · 프로필 | `character` |
| H07 내 칸 이름 · 아바타 (카메라를 켜도 영상 대신 아바타, 9번) | `me`(닉네임) · `avatar`(Live2D 모델. 프론트가 이 모델로 사용자 동작을 따라 함) |
| H07 타이머 '00:17:23 / 00:50:00' | `startedAt`, `targetMinutes`, `serverTime` |

- 테이블: `study_sessions`, `session_characters`(1행), `user_character_relations`(처음 만나면 생성) · 인증: 필요
- 감지 방식(`detect_mode`)은 DB에서 빠져서 받지 않음

#### [POST] /api/sessions

#### 🛫 Request

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| roomId | Long | O | 들어갈 수 있는 룸 (현재 1개) |
| characterId | Long | O | 함께할 캐릭터 1명 (그 룸 캐릭터만 가능) |
| targetMinutes | Integer | O | 3분, 또는 5~180분 5분 단위 (2026-11-05: 최소 5분 → 3분) |

```json
{ "roomId": 1, "characterId": 1, "targetMinutes": 50 }
```

#### 🛬 Response

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| sessionId | Long | 세션 id |
| startedAt | String | 시작 시각 (서버) |
| targetMinutes | Integer | 목표 시간 |
| room | Object | `id`, `name`, `backgroundImageUrl` |
| character | Object | `id`, `name`, `profileImageUrl`, `modelJsonUrl`, `scale`, `offsetX`, `offsetY` |
| avatar | Object | `id`, `name`, `profileImageUrl`, `modelJsonUrl`, `scale`, `offsetX`, `offsetY` |
| me | Object | `nickname`, `profileImageUrl`(아바타 썸네일) |
| serverTime | String | 서버 시각 (타이머 보정) |

```json
{
  "sessionId": 512,
  "startedAt": "2026-11-07T05:00:02Z",
  "targetMinutes": 50,
  "room": { "id": 1, "name": "밤의 호그와트 도서관", "backgroundImageUrl": "https://cdn.../room1-bg.png" },
  "character": { "id": 1, "name": "말포이", "profileImageUrl": "https://cdn.../chaerin.png", "modelJsonUrl": "https://cdn.../live2d/chaerin/v1/chaerin.model3.json", "scale": 1.0, "offsetX": 0, "offsetY": 12 },
  "avatar": { "id": 1, "name": "기본 아바타", "profileImageUrl": "https://cdn.../avatar1.png", "modelJsonUrl": "https://cdn.../live2d/avatar1/v1/avatar1.model3.json", "scale": 1.0, "offsetX": 0, "offsetY": 12 },
  "me": { "nickname": "승희", "profileImageUrl": "https://cdn.../avatar1.png" },
  "serverTime": "2026-11-07T05:00:02Z"
}
```

> ✅ **201 Created**

> ❌ **400** INVALID_TARGET_MINUTES · CHARACTER_NOT_IN_ROOM · **403** ONBOARDING_REQUIRED · **404** ROOM_NOT_FOUND · **409** SESSION_ALREADY_IN_PROGRESS (`detail.sessionId`, 연타로 동시에 두 번 보내도 나중 것은 409) → H18 팝업

#### 이 API 다음에 할 일

1. '공부 상태 기록'으로 `FOCUS` 1건 → `eventId` 받기
2. '상태 반응 받기'에 그 `eventId` → 캐릭터 인사 (대사 + 모션 + 표정)
3. 1분마다 heartbeat 시작

## 17. POST /api/sessions/{sessionId}/resume — 세션 이어하기
- 분류: 공부 세션
- 설명: 비정상 종료된 세션 이어하기. 끊긴 구간은 자동 휴식 처리
- 와이어프레임: H18 이어서 시작할게 · H04 진행 중인 공부로
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/93ab9a07e59283a19ef981931ec863cf

#### 한 줄 요약

비정상으로 나갔던 세션(탭 닫힘 · 새로고침 · 브라우저 종료 · 네트워크 끊김)을 **이어서 시작**합니다. 끊긴 동안은 **자동으로 쉬는 시간(BREAK)** 으로 처리돼 공부 시간에 들어가지 않습니다.

#### 와이어프레임 대조

| 화면 요소 | 이 API가 하는 일 |
| --- | --- |
| H18 '다시 공부를 시작할까요? …에서 하던 공부를 이어서 진행해요' | 내 정보 · 홈 조회의 `activeSession`이 있으면 팝업 |
| H18 '이어서 시작할게' | 이 API → H07로 복귀 |
| H18 '닫기' | 호출 안 함. 세션은 그대로 남음 |
| H03 '이어서 공부하기' · H04 '진행 중인 공부로 돌아가기' | 이 API |

- 테이블: `study_sessions`, `session_events` · 인증: 필요

#### [POST] /api/sessions/{sessionId}/resume

#### 🛬 Response

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| sessionId · targetMinutes · room · character · avatar · me · serverTime |  | '공부 시작' 응답과 같음 (`startedAt`은 없음. 타이머는 `studySeconds`로) |
| studySeconds | Integer | 지금까지 공부 시간 → H07 타이머를 여기서부터 이어서 표시 |
| breakSeconds | Integer | 지금까지 휴식 시간 (끊긴 구간 포함) |

```json
{
  "sessionId": 512, "targetMinutes": 50,
  "room": { "id": 1, "name": "밤의 호그와트 도서관", "backgroundImageUrl": "https://cdn.../room1-bg.png" },
  "character": { "id": 1, "name": "말포이", "profileImageUrl": "https://cdn.../chaerin.png", "modelJsonUrl": "https://cdn.../live2d/chaerin/v1/chaerin.model3.json", "scale": 1.0, "offsetX": 0, "offsetY": 12 },
  "avatar": { "id": 1, "name": "기본 아바타", "profileImageUrl": "https://cdn.../avatar1.png", "modelJsonUrl": "https://cdn.../live2d/avatar1/v1/avatar1.model3.json", "scale": 1.0, "offsetX": 0, "offsetY": 12 },
  "me": { "nickname": "승희", "profileImageUrl": "https://cdn.../avatar1.png" },
  "studySeconds": 1043, "breakSeconds": 420,
  "serverTime": "2026-11-07T05:31:10Z"
}
```

> ✅ **200 OK**

> ❌ **404** SESSION_NOT_FOUND · **409** SESSION_ENDED (이미 자동 마감됨 → 리포트 조회로)

#### 서버가 하는 일

1. 마지막 상태가 `BREAK`가 아니면, 마지막 heartbeat 시각에 `BREAK` 이벤트를 넣음 (끊긴 구간 = 휴식). 마지막 heartbeat 뒤에 온 이벤트가 있으면 그 이벤트 시각에 넣음 (그 상태가 끊긴 동안 이어지지 않게)
2. 지금 시각에 `FOCUS` 이벤트를 넣고 `last_heartbeat_at` 갱신
3. 서버가 넣은 이 두 이벤트는 호감도에 반영하지 않음

#### 이 API 다음에 할 일

1. 캐릭터 Live2D를 다시 받아 H07 그리기 (새로고침이면 메모리에서 사라졌으므로)
2. 이어하기 응답에는 `focusEventId`를 넣지 않음 (2026-11-03 결정). 이어하기 직후에는 반응 없이 바로 공부 재개. 프론트는 `FOCUS`를 따로 보내지 않음 (서버가 이미 넣음)
3. heartbeat 재시작

## 18. POST /api/sessions/{sessionId}/events — 공부 상태 기록
- 분류: 공부 세션
- 설명: 상태가 바뀌거나 동작(졸음 · 하품 · 손 흔들기 · 고개 돌림)이 감지될 때마다 1건씩 전송 → 저장 + 호감도 반영 → eventId + 캐릭터별 '알아채는' 동작 · 호감도
- 와이어프레임: H07 자리 감지 · 동작 감지 · H08 쉬는 시간 · H19 일시정지
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/7d3b9a07e59282318ed48104d03bd4c9

#### 한 줄 요약

![image.png](notion-file-block://b74b9a07-e592-82d0-a789-010ebef8cbd1/3bf86178-a4f3-4541-a33a-9e890e79dd3b?space_id=838cb83b-0307-45ee-872a-9da44a7ba809&name=image.png)

공부 중 상태가 바뀌거나 동작이 감지되면 **그때마다 1건씩** 보냅니다. 서버는 저장하고 **호감도를 바로 반영**한 뒤, `eventId`와 함께 캐릭터가 바로 재생할 **'알아채는' 짧은 동작(모션 · 표정)** 을 돌려줍니다. 대사는 19번으로 받습니다.

> 📏 시간 계산(자리 지킴 · 자리 비움)은 **자리에 있다 / 없다**뿐입니다. 졸음 · 하품 · 손 흔들기 · 고개 돌림은 캐릭터 반응을 위한 **동작 이벤트**라 시간 계산에 쓰지 않습니다. 고개 돌림도 '시선 이탈 시간'으로 세지 않습니다 (DB에서도 GAZE_AWAY 제거).

#### 이벤트 8종

> 2026-11-04: 동작 이벤트 3종(`DROWSY` · `YAWN` · `WAVE`) 추가. 프론트가 졸음 · 하품 · 손 흔들기를 감지할 수 있게 됨

> 2026-11-04 (2차): 이름 '상태 변화 전송' → '공부 상태 기록'. 동작 이벤트는 같은 type 1분에 한 번(프론트 · 서버 둘 다), 서버는 1분 안에 다시 오면 무시. 시간 · 상태 판단은 상태 이벤트 4종만(공부 종료 정산 · 세션 이어하기 · heartbeat 포함). 타입은 서버 enum `STATE` / `MOTION`

> 2026-11-04 (3차): 동작 이벤트 `HEAD_TURN`(고개 돌림) 추가. 프론트 감지가 안정돼서. 다른 동작 이벤트와 같이 상태 · 시간 계산에 쓰지 않음

> 2026-11-04 (호감도 최종안): 동작 이벤트는 0점(확정). 첫 만남 +5 삭제 → 연속 출석. 돌아옴 0점(확정). 자리 비움은 1분 넘게 비웠을 때만 감점(임시)

| type | 종류 | 언제 | 시간 분류 | 호감도 (숫자는 임시, 설정 `capsule.relation`) |
| --- | --- | --- | --- | --- |
| `FOCUS` | 상태 | 공부 시작 직후, 휴식 · 일시정지 끝 | 자리 지킴 | 세션 첫 FOCUS = 그날 출석. 어제도 출석했으면 연속 2일째부터 하루 한 번 +1 (STREAK). 끊기면 1일째부터 (감점 없음) |
| `AWAY` | 상태 | **10초 이상 연속**으로 안 보임 | 자리 비움 | 0 (끝날 때 판단) |
| `RETURN` | 상태 | 자리 비움 뒤 다시 보임 (복귀 횟수 +1) | 자리 지킴 | 0. 직전 `AWAY`부터 **1분 넘게** 비웠으면 −1 (하루 최대 −3) |
| `BREAK` | 상태 | 쉬는 시간 · 일시정지 시작 | 휴식 (공부 시간에서 제외) | 0. 자리 비운 채 쉬기 시작하면 `RETURN`과 같은 감점 판단 |
| `DROWSY` | 동작 | 졸음 감지 | 영향 없음 | 0 |
| `YAWN` | 동작 | 하품 감지 | 영향 없음 | 0 |
| `WAVE` | 동작 | 카메라 보고 손 흔듦 | 영향 없음 | 0 |
| `HEAD_TURN` | 동작 | 고개를 돌려 화면에서 눈을 뗌 | 영향 없음 | 0 |

- 자리 비움 감점은 `AWAY`를 끝낸 상태 이벤트(`RETURN` · `BREAK` · `FOCUS`)에서 비운 시간(그 이벤트 `occurredAt` − `AWAY` `occurredAt`)을 보고 정함. 자리 비운 채로 공부를 끝내면 감점 없음 (확정 필요)
- 날짜(연속 출석 · 하루 상한)는 한국 날짜 기준

- `AWAY`의 `occurredAt`은 **안 보이기 시작한 시각** (10초 기다린 시각이 아님). 10초 안에 다시 보이면 아무것도 안 보냄 (고개 숙임 · 조명으로 잠깐 끊기는 것 무시)
- 동작 이벤트는 감지된 순간 1건만. `occurredAt`은 감지 시각. **같은 type은 1분에 한 번** (프론트 · 서버 둘 다 적용)
- ⚠️ 기획 확인 필요: 고개 돌림을 '딴짓'으로 보고 자리 비움 시간에 넣을지. 지금은 넣지 않음 (자리에 있는 동안의 순간 동작으로만 봄)
- 호감도 0인 이벤트는 `relation_logs`에 기록하지 않음 (DB `delta <> 0`)

#### 와이어프레임 대조

| 화면 요소 | 보낼 이벤트 |
| --- | --- |
| H07 공부 시작 직후 | `FOCUS` (H18 이어서 시작 직후에는 보내지 않음. 서버가 넣음, 17 참고) |
| H07 '● 얼굴 확인됨'이 10초 이상 꺼짐 (H09 '자리 비움') | `AWAY` |
| H07 다시 얼굴 확인됨 (H09 '복귀 n회') | `RETURN` |
| H08 쉬는 시간 시작 / '목표 정하고 공부 이어가기' | `BREAK` / `FOCUS` |
| H19 '일시정지' / '계속하기' | `BREAK` / `FOCUS` |
| H07 졸음 · 하품 · 손 흔들기 · 고개 돌림 감지 (와이어프레임에 아직 없음) | `DROWSY` · `YAWN` · `WAVE` · `HEAD_TURN` |

- 테이블: `session_events`, `relation_logs`(SESSION_EVENT · STREAK), `user_character_relations`, `live2d_reaction_maps`(모션 · 표정) · 인증: 필요

#### [POST] /api/sessions/{sessionId}/events

#### 🛫 Request (이벤트 1건)

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| type | String | O | 위 8종: `FOCUS` / `AWAY` / `RETURN` / `BREAK` / `DROWSY` / `YAWN` / `WAVE` / `HEAD_TURN` |
| occurredAt | String | O | 발생 시각 (ISO) |

```json
{ "type": "AWAY", "occurredAt": "2026-11-07T05:21:04Z" }
```

#### 🛬 Response

> 2026-11-04: 모션 · 표정 · 호감도를 반응 받기(19)에서 이쪽으로 옮김. 캐릭터별 배열 `characters`

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| eventId | Long | 저장된 이벤트 id → 19번에 사용. 무시된 경우 null (아래 '규칙') |
| characters | Array | 세션 캐릭터마다 1개 (1:1 룸이면 1개). 무시된 경우 `[]` |
| characters[].characterId | Long | 캐릭터 id |
| characters[].motion | Object | `group`, `file` — 받자마자 재생하는 짧은 '알아채는' 동작 (없으면 null) |
| characters[].expression | Object | `name`, `file` — 모션 위에 겹쳐 적용 (없으면 null) |
| characters[].affinity | Object | 이 이벤트로 바뀐 호감도: `delta`(0이면 변화 없음), `points`(0~100), `stage` · `stageName`(관계 단계, 호감도가 내려가면 같이 내려감), `level`(공부 시간 레벨, 이벤트로는 안 바뀜), `levelUp`(옛 필드, 늘 false, 지울 예정) |

```json
{
  "eventId": 12344,
  "characters": [
    {
      "characterId": 1,
      "motion": null,
      "expression": { "name": "웃음", "file": "exp/expression6.exp3.json" },
      "affinity": { "delta": -1, "points": 47, "stage": 2, "stageName": "친한 사이", "level": 1, "levelUp": false }
    }
  ]
}
```

> ✅ **201 Created** · 같은 (`type`, `occurredAt`)을 다시 보내면 **200 + 기존 응답** (재전송 안전. 호감도는 다시 바뀌지 않음)

> ❌ **400** INVALID_EVENT · **404** SESSION_NOT_FOUND · **409** SESSION_ENDED

#### 규칙

- 무시: 저장하지 않고 **200** `{"eventId": null, "characters": []}`. 프론트는 19번을 부르지 않음
  - 휴식(`BREAK`) 중에 온 `AWAY` · `RETURN` · 동작 이벤트
  - 같은 동작 type이 1분 안에 다시 옴 (서버 쿨다운)
- 시간 · 상태 판단은 상태 이벤트 4종(`FOCUS` · `AWAY` · `RETURN` · `BREAK`)만 봄: 공부 종료 정산, 세션 이어하기, heartbeat의 '마지막 이벤트'는 모두 이 4종 중 마지막. 동작 이벤트는 사이에 끼어도 구간을 끊지 않음
- 타입 검증은 서버 enum (상태 `STATE` / 동작 `MOTION` 구분). 모르는 type은 **400** INVALID_EVENT
- 18의 모션 · 표정은 `live2d_reaction_maps`에서 trigger 기준으로 서버가 고름 (trigger 규칙은 19와 같음, `AWAY`는 강도 1). 어떤 trigger에 어떤 동작이 있는지는 디자이너 에셋에 따라 바뀜. 없으면 null이고 프론트는 재생하지 않음
- `occurredAt`이 세션 시작보다 이르면 거절하지 않고 시작 시각으로 맞춤 (프론트 시계가 조금 늦어도 시작 직후 FOCUS가 실패하지 않게)
- 휴식 끝은 `FOCUS`, 자리 비움에서 돌아오면 `RETURN` (복귀 횟수가 휴식 때문에 부풀지 않게)
- ⚠️ 기획 확인 필요 (J-01): 자리 비움 감점 크기(−1)와 기준(1분) · 하루 최대(−3), 연속 출석 점수(+1)
- 전송 실패 시 같은 내용으로 재시도 → 서버가 (타입, 시각)으로 중복을 걸러 같은 응답을 돌려줌
- 카메라를 끄면 `AWAY` · 동작 이벤트를 보내지 않음 (끈 동안은 자리 지킴으로 계산). 영상 · 얼굴 좌표는 보내지 않음
- 카메라를 켜면 내 칸에는 영상 대신 아바타를 보여주고 감지는 그대로 함 (2026-11-05 기획). 아바타가 사용자 동작을 따라 하는 것은 **프론트가 카메라 인식 결과로 매 프레임 직접** 처리하고 서버를 부르지 않음: 얼굴(고개 각도 · 눈 · 입)과 손(손목 위치 → 팔 · 손목 값)을 실시간으로 따라가서, 하품 · 졸음 · 손 흔들기가 시작하고 멈추는 순간도 사용자와 같음 (정해진 동작을 시간만큼 재생하지 않음). 그래서 위 1분 쿨다운과 상관없이 따라 하고, 얼굴 좌표도 서버로 보내지 않음. 이 API(동작 이벤트)는 지금처럼 캐릭터 반응 · 기록용으로만 보냄

## 19. POST /api/sessions/{sessionId}/events/{eventId}/reaction — 상태 반응 받기
- 분류: 대화
- 설명: eventId로 캐릭터 대사(LLM) + 감정 + 감정에 맞는 모션 · 표정 받기
- 와이어프레임: H07 말풍선 · 캐릭터 모션 · 표정
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/3d4b9a07e59282279b90814c77fa1129

#### 한 줄 요약

18번에서 받은 `eventId`로 그 상황에 맞는 **캐릭터 대사**를 받습니다. **대사의 감정에 맞는 모션 · 표정**을 같이 줍니다. 호감도는 18번 응답에 이미 있어서 여기엔 없습니다.

> 2026-11-04 (PM 결정 · 캐릭터 설정 DB): 대사는 캐릭터 설정(`character_lines`)에 **미리 쓴 대사를 상황마다 순서대로** 돌려 쓴다. AI 생성은 설정으로 꺼 둠(데모데이 이후). 그 캐릭터에 그 상황 대사가 없으면 서버 공통 고정 대사
> 2026-11-05 (승희, ADR 0022 결정 18): **상태 변화**(인사 · 자리 비움 · 돌아옴 · 쉬기 · 쉬기 끝)는 LLM이 대사와 감정(표정)을 같이 고른다. 미리 쓴 대사는 말투 예시 + 5초 대체 대사. **순간 동작**(졸음 · 하품 · 손 흔들기 · 고개 돌림)은 미리 쓴 대사 · 표정 그대로(매핑). 응답 모양은 그대로

#### 18 모션과 19 모션

| | 18 응답 | 19 응답 |
| --- | --- | --- |
| 역할 | 알아채는 즉시 동작 (사용자 쪽으로 돌아보기 등) | 말할 때 감정 동작 |
| 재생 | 받자마자 | 대사와 함께. null이면 18 동작 유지 |

#### 언제 부르나

> 2026-11-04: 동작 이벤트 3종(`DROWSY` · `YAWN` · `WAVE`)의 반응 추가. trigger 이름은 이벤트 타입 그대로

> 2026-11-04 (4차): `HEAD_TURN`(고개 돌림) 반응 추가

| 상황 | 부르는 방법 |
| --- | --- |
| 공부 시작 직후 `FOCUS` | `eventId`만 → 인사. 이어하기 직후에는 부르지 않음 (17: 반응 없이 바로 재개) |
| `AWAY` | `intensity: 1` → 1분 지나도 안 오면 같은 `eventId`로 `2` → 5분이면 `3` |
| `RETURN` · `BREAK` · 휴식 뒤 `FOCUS` | `eventId`만 |
| `DROWSY` · `YAWN` · `WAVE` · `HEAD_TURN` | `eventId`만 |
| 18에서 `eventId`가 null | 부르지 않음 |
| H07 '☾ 조용히 집중' 모드 | 부르지 않음 (호감도는 18에서 이미 반영됨) |

#### 와이어프레임 대조

| 화면 요소 | 응답 필드 |
| --- | --- |
| H07 캐릭터 칸 말풍선 '잠깐 자리 비운 거야?' · '채린 · 말 거는 중' | `characters[].content` |
| H07 캐릭터 칸 Live2D 동작 · 얼굴 | `characters[].motion`, `characters[].expression` |

- 테이블: `live2d_reaction_maps`(서버가 하나 고름), `llm_usages` · 인증: 필요

#### [POST] /api/sessions/{sessionId}/events/{eventId}/reaction

#### 🛫 Request

> 2026-11-03: 요청 본문(requestId · intensity · tts) 추가, 대사 필드 이름은 `content`로 통일 (메시지 전송과 같음)

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| requestId | String (UUID) | O | 프론트가 만든 요청 id. 응답에 그대로 돌아옴 (늦게 온 응답 걸러내기) |
| intensity | Integer | X | `AWAY`일 때만: 1 눈치 · 2 말 걸기(1분) · 3 서운함(5분). 없으면 1, 다른 이벤트는 무시. AWAY인데 1\~3 밖이면 400 INVALID_REQUEST |
| tts | Boolean | X | true면 대사 음성도 받음 (기본 false) |

```json
{ "requestId": "5b1f...", "intensity": 1, "tts": false }
```

#### 🛬 Response

> 2026-11-04: 캐릭터별 배열 `characters`로 바꿈. `trigger`는 뺌, 호감도는 18번 응답으로 옮김, 캐릭터마다 `emotion` 추가

> 2026-11-04 (3차): 감정 · 동작 목록은 고정하지 않음. 디자이너가 만든 에셋에 따라 바뀌므로 데이터(`live2d_reaction_maps`)로 정함

> 2026-11-04 (2차): `characters`는 이번에 말하는 캐릭터만, 모션 · 표정은 감정 기준으로 고름 (18의 '알아채는' 동작과 역할이 다름)

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| requestId | String | 요청의 `requestId` 그대로 |
| characters | Array | 이번에 말하는 캐릭터만 (1:1 룸이면 1개) |
| characters[].characterId | Long | 캐릭터 id |
| characters[].content | String | 말풍선 대사 (2줄 이내) |
| characters[].emotion | String | 대사의 표정 이름. **목록은 캐릭터 설정(`character_profiles.expressions`)이 정한다** (말포이: `삐짐` · `행복`(함박웃음) · `경멸` · `하품` · `화남` · `웃음` · `수줍음` · `집중`, 승희 확정 2026-11-05). 에셋이 없는 표정은 서버가 대체 표정(`expression_fallbacks`)으로 고른다(말포이는 8개 모두 있어 없음). LLM이 목록 밖 감정 이름을 쓰면 같은 대체 표로 목록 안 표정으로 바꾸고(말포이: 걱정 → 집중, 질투 · 서운함 → 삐짐 등), 표에도 없으면 null (2026-11-05). 말포이 동작(모션)은 숨쉬기 같은 기본 동작뿐이라 `motion`은 보통 null. 설정이 없는 캐릭터는 공통 후보(`HAPPY` · `SHY` · `ANGRY` · `SAD` · `SURPRISED` · `NEUTRAL`). ⚠️ 고정 대사(`fallback: true`)일 때 값은 확정 필요 (지금 구현은 null) |
| characters[].motion | Object | `group`, `file` — 감정에 맞는 모션 (없으면 null → 18 동작 유지) |
| characters[].expression | Object | `name`, `file` — 감정에 맞는 표정 (없으면 null) |
| characters[].fallback | Boolean | LLM 5초 초과로 고정 대사를 썼는지 |
| characters[].audioBase64 | String | 대사 음성 (mp3). 요청에 `tts: true`일 때만, 아니면 null |

```json
{
  "requestId": "5b1f...",
  "characters": [
    {
      "characterId": 1,
      "content": "야, 어디 가? …금방 와.",
      "emotion": "삐짐",
      "motion": null,
      "expression": { "name": "삐짐", "file": "exp/expression1.exp3.json" },
      "fallback": false,
      "audioBase64": null
    }
  ]
}
```

> ✅ **200 OK**

> ❌ **400** INVALID_REQUEST (`requestId`가 없음, `AWAY`인데 `intensity`가 1~3 밖) · **404** SESSION_NOT_FOUND · EVENT_NOT_FOUND · **409** SESSION_ENDED

#### 서버가 하는 일

1. 이벤트 타입 + 직전 상태 이벤트로 trigger 결정 (응답에는 넣지 않음): 첫 FOCUS = `GREETING`, 휴식 뒤 FOCUS = `BREAK_END`, `AWAY` · `RETURN` · `BREAK`, 동작은 type 그대로(`DROWSY` · `YAWN` · `WAVE` · `HEAD_TURN`)
2. 상태 변화(`GREETING` · `AWAY` · `RETURN` · `BREAK` · `BREAK_END`): LLM이 대사 + 감정을 한 번에 만든다. 감정은 그 캐릭터의 표정 목록(`character_profiles.expressions`) 중에서. 넣는 것: 캐릭터 페르소나 + 지금 관계 단계의 관계 설명 · 말투 샘플 · 상태 변화 예시(그 단계의 미리 쓴 대사), 캐릭터 오늘 근황(`character_lines` TODAY, 요일별), 지난 세션 흐름, 이번 세션 최근 대화, 일어난 일(trigger를 "방금 일어난 일" 한 줄로. 예: AWAY = 아직 안 돌아옴) (ADR 0022 결정 19). 5초 넘거나 실패하면 미리 쓴 대사(`fallback: true`). 순간 동작(`DROWSY` · `YAWN` · `WAVE` · `HEAD_TURN`): 미리 쓴 대사와 그 칸의 표정을 순서대로 (AI 안 씀)
3. 감정에 맞는 모션 · 표정을 `live2d_reaction_maps`에서 하나 고름 (같은 감정의 행이 여러 개면 같은 확률)
4. `tts: true`면 음성 생성
- 호감도는 바꾸지 않음 (18번에서 끝남). 같은 `eventId`로 intensity를 올려 다시 불러도 마찬가지
- ⚠️ 확정 필요 (J-04): 위 3번처럼 모션 · 표정을 감정으로 고르면 `intensity` 2 · 3은 대사(와 감정)에만 쓰인다. 그러면 `live2d_reaction_maps`의 `AWAY` 강도 2 · 3 행을 고르는 곳이 없다 (18은 강도 1). 강도별 동작을 어디에 쓸지 정하기

#### 프론트 참고

- 응답의 `requestId`가 마지막으로 보낸 것이 아니면 버림 (복귀했는데 늦게 도착한 '자리 비웠어?' 방지)
- `motion` 재생 → `expression` 적용 → `content`를 말풍선 · 채팅 로그에 표시 → 입 모션
- `emotion`은 표시 · 기록용 문자열이다. 목록이 디자이너 에셋에 따라 바뀌므로 고정 enum으로 분기하지 말고, 화면은 서버가 준 `motion` · `expression` 파일로 그린다 (null이면 재생하지 않음)

## 20. POST /api/sessions/{sessionId}/messages — 메시지 전송
- 분류: 대화
- 설명: 대화 → 캐릭터별 답장 + 감정 모션 · 표정 + 바뀐 호감도 (TTS 선택)
- 와이어프레임: H08 휴식 대화 · H20 공부 중 채팅
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/62bb9a07e592830dba5701795f33ec24

#### 한 줄 요약

캐릭터 1명을 골라 대화합니다. **답장 · 감정 모션 · 호감도 변화**를 한 번에 받습니다. 응답은 그 캐릭터 1명의 답장이고(필드는 19번 `characters[]` 한 칸과 같음), 채팅으로 바뀐 호감도(`affinity`)가 추가됩니다. `eventId`는 없습니다 (채팅은 `session_events`에 저장되지 않음). 크레딧이 빠져서 횟수 제한은 없고, 남용 방지용 속도 제한만 있습니다.

#### 와이어프레임 대조

| 화면 요소 | 이 API가 하는 일 |
| --- | --- |
| H08 · H20 '친구와 대화' 패널 '메시지 입력 ↑' | `content`, `inputType = TEXT` |
| H08 · H20 빠른 답장 '파이팅' · '졸려' · '이거 어려워' | `inputType = QUICK` |
| H08 · H20 '[친구 메시지]' · '입력 중…' | `content` (기다리는 동안 입력 중 표시) |
| H07 캐릭터 칸 (답장할 때 동작 · 얼굴) | `motion`, `expression` |
| H08 · H20 캐릭터 탭 채린 · 수아 · 다희 | 요청 `characterId`. 세션 캐릭터 1명만 실제, 나머지 목업 |
| H08 · H20 '하드코딩 / llm' 토글, '옵션 올리기', '베이직 하루 3회' | **제거** |

- 테이블: `conversations`, `chat_messages`, `relation_logs`(CHAT), `llm_usages` · 인증: 필요

#### [POST] /api/sessions/{sessionId}/messages

#### 🛫 Request

> 2026-11-04: 대화할 캐릭터 `characterId` 추가

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| requestId | String (UUID) | O | 재전송돼도 한 번만 처리 |
| characterId | Long | O | 대화할 캐릭터 (그 세션 캐릭터만 가능) |
| content | String | O | 1~300자 |
| inputType | String | O | `TEXT` 직접 입력 / `QUICK` 빠른 답장 |
| tts | Boolean | X | true면 답장 음성도 받음 (기본 false) |

```json
{ "requestId": "9c3a...", "characterId": 1, "content": "이거 너무 어려워", "inputType": "QUICK", "tts": false }
```

#### 🛬 Response

> 2026-11-03: 응답을 '상태 반응 받기'와 같은 모양으로 맞춤 (모션 · 표정 · 호감도 · fallback · 음성). `eventId`는 뺌 (채팅은 상태 이벤트가 아니라 DB `session_events`에 없음)

> 2026-11-04: 19번과 같은 캐릭터별 배열 `characters[]`로 바꿈. 캐릭터마다 `emotion` 추가, `affinity`는 캐릭터 안으로

> 2026-11-04 (2차): 요청에서 캐릭터 1명을 고르므로 `characters[]` 배열을 없애고 그 캐릭터의 필드를 맨 위로 올림 (`characterId` 포함)

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| requestId | String | 요청 id 그대로 (늦게 온 답 걸러내기) |
| characterId | Long | 요청의 `characterId` 그대로 |
| content · emotion · motion · expression · fallback · audioBase64 | | 19번 `characters[]` 한 칸과 같음 |
| affinity | Object | 이 대화로 바뀐 호감도: `delta`(0이면 변화 없음), `points`, `stage` · `stageName`, `level`, `levelUp`(옛 필드). 필드는 18번 `affinity`와 같음. `fallback`이면 `delta` 0 |

> 2026-11-04 (호감도 최종안): 점수는 LLM이 주지 않음. 말의 종류를 말 패턴으로 고르고(A안, 확정) 캐릭터 점수표로 정함. 오르기는 어렵고 내려가기는 쉬움

| 말의 종류 | 점수 (임시, 캐릭터마다 다를 수 있음) |
| --- | --- |
| 칭찬 `COMPLIMENT` · 고마움 `THANKS` | +1 |
| 고민 털어놓기 `CONFIDE` | +2 (세션당 1번) |
| 성의 없는 단답 `LOW_EFFORT` · 짜증 · 불평 `COMPLAINT` · 평범한 말 `NEUTRAL` | 0 |
| 무례 · 욕 `RUDE` | −2, 같은 세션에서 또 하면 −3 |
| 혐오 · 성적 `HATE_OR_SEXUAL` | **지금 값의 −30%** (최소 −5). 그날은 더 오르지 않음 (대사는 평소대로) |

- 오르는 쪽만 하루 +15까지 (캐릭터별, 한국 날짜). 내려가는 쪽은 상한 없음
- 최근 10턴 안에 같은 종류가 세 번째면 0점 (칭찬 연타 방지, 오르는 쪽만)
- 혐오 · 성적은 말 패턴이 걸리면 결정 모델이 한 번 더 확인 (확신도 0.8 미만이면 무례로)
- 답이 늦어 기본 대사가 나간 턴은 0점

```json
{
  "requestId": "9c3a...",
  "characterId": 1,
  "content": "갑자기 그런 말 하지 마. …집중 깨져.",
  "emotion": "수줍음",
  "motion": null,
  "expression": { "name": "수줍음", "file": "exp/expression7.exp3.json" },
  "affinity": { "delta": 1, "points": 51, "stage": 2, "stageName": "친한 사이", "level": 1, "levelUp": false },
  "fallback": false,
  "audioBase64": null
}
```

> ✅ **200 OK**

> ❌ **400** EMPTY_MESSAGE · INVALID_REQUEST (300자 초과) · CHARACTER_NOT_IN_SESSION · **404** SESSION_NOT_FOUND · **409** SESSION_ENDED · **429** TOO_MANY_REQUESTS (1분에 10번)

#### 서버가 하는 일

1. 사용자 메시지 저장 (`chat_messages`)
2. 결정 모델: 캐릭터가 아는 사실(최대 20개) + "선택 안 함" 중 이번 말에 꺼낼 사실 하나를 고름 (1.5초 안에 못 고르면 선택 안 함)
3. LLM: 캐릭터 페르소나 + 지금 관계 단계의 관계 설명 · 말투 샘플(ADR 0022 결정 19, 문구는 데모용 임시 · 확정 필요) + 캐릭터 오늘 근황(`character_lines` TODAY, 요일별) + 고른 사실 하나(있을 때만) + 지난 세션 흐름(최근 2장) + 이번 세션 대화(최근 15줄) + 일정(세션 첫 대화만) → 답장 + 감정
4. 감정에 맞는 모션 · 표정을 `live2d_reaction_maps`에서 고름
5. 말의 종류(`chat_messages.signal`) → 점수 → 0~100에서 실제로 바뀐 만큼 `relation_logs`(CHAT, `request_id`, `signal`)에 기록
6. 5초 넘으면 대체 대사 + `fallback: true`, 호감도 변화 없음
7. 답장을 보낸 뒤 따로: 이번 말에서 기억할 것을 결정 모델이 고름 (무시 · 추가 · 고치기 · 지우기) → `character_memories`. 20개를 넘으면 가장 덜 중요한 하나를 버림. 전화번호 같은 긴 숫자는 저장하지 않음

#### 규칙

- 답 길이 · 구성: 공부 중 1\~2문장(공부 얘기 중심), 쉬는 시간 2\~4문장(공부 재촉 없이 수다). 지금 상태는 마지막 상태 이벤트(18)로 판단: `BREAK`면 쉬는 시간
- 공부 중에 수다가 길어지면(최근 10분에 사용자 말 8번 이상, 이번 말 포함, 임시 J-14) 캐릭터가 짧게 받아 주고 공부로 돌려보낸다. 막지는 않는다 (횟수 제한 없음은 그대로). 쉬는 시간에는 하지 않음
- 같은 `requestId`를 다시 보내면 저장된 답장을 그대로 돌려줌. 속도 제한(1분에 10번) 횟수에 세지 않음. 이때 `affinity.delta`는 0, `emotion` · `motion` · `expression`은 null (처음 응답의 값은 다시 주지 않음). 첫 요청이 아직 처리 중일 때 온 재전송은 첫 요청이 끝날 때까지 기다렸다가 같은 답장을 받음
- 일시정지 중에는 프론트가 입력을 막음

## 21. POST /api/sessions/{sessionId}/heartbeat — heartbeat
- 분류: 공부 세션
- 설명: 1분마다 생존 신호. 비정상 종료 판단 기준
- 와이어프레임: H07 · H08 · H19 동안
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/e6db9a07e59283b48e8681b64ccebdfb

#### 설명

탭이 열려 있다는 신호입니다. **1분마다** 보냅니다(휴식 · 일시정지 중에도). 비정상 종료를 알아채는 기준이자, 이어하기에서 '언제부터 끊겼는지'의 기준입니다.

#### 와이어프레임 대조

화면 요소 없음. H07 · H08 · H19가 떠 있는 동안 계속.

- 테이블: `study_sessions.last_heartbeat_at` · `heartbeat_study_seconds` · `heartbeat_present_seconds` · 인증: 필요

#### [POST] /api/sessions/{sessionId}/heartbeat

#### 🛫 Request

> 2026-11-04: 지금까지의 공부 시간을 같이 보냄. 비정상 종료(자동 마감) 때 마지막으로 받은 이 값으로 세션 기록을 저장

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| studySeconds | Integer | O | 지금까지 공부 시간(초). 쉬는 시간 제외 = 자리 지킴 + 자리 비움 |
| presentSeconds | Integer | O | 그중 자리 지킴(초) |

```json
{ "studySeconds": 1800, "presentSeconds": 1620 }
```

> ✅ **204 No Content**

> ❌ **400** INVALID_STUDY_TIME (본문이 없거나 공부 종료와 같은 검사에 걸림: 0 ≤ `presentSeconds` ≤ `studySeconds` ≤ 시작부터 지난 시간) · **404** SESSION_NOT_FOUND · **409** SESSION_ENDED → 이미 끝난 세션. 리포트 조회로

- 400이어도 **생존 신호(`last_heartbeat_at`)는 갱신**하고, 값만 저장하지 않음 (시계 차이로 1초 넘쳐도 끊긴 세션이 되지 않게)
- 이어하기(17)를 하면 저장된 값을 이어하기 응답의 `studySeconds` 기준으로 맞춤 (다음 heartbeat 전에 또 끊겨도 옛 값으로 마감되지 않게)

#### 비정상 종료 정책

| 상황 | 서버 처리 |
| --- | --- |
| heartbeat가 2분 넘게 없음 | '끊긴 상태'로 보고 기다림 (세션은 진행 중 유지) |
| 다시 로그인 · 새로고침 | 내 정보 · 홈 조회에 `activeSession` → H18 → 세션 이어하기 |
| 6시간 동안 이어하지 않음 | 배치가 자동 마감: `ended_at = last_heartbeat_at`, `end_type = AUTO_CLOSED`, 정산 · 관계 갱신까지. 공부 시간 · 자리 지킴은 마지막 heartbeat 값으로 저장 (받은 값이 없으면 `session_events`로 계산) |

## 22. POST /api/sessions/{sessionId}/end — 공부 종료
- 분류: 리포트
- 설명: 공부 종료 + 정산 + 관계 갱신 → 공부 시간 · 자리 지킴률 · 달성률 · 호감도 변화
- 와이어프레임: H17 · H08 오늘은 여기까지 → H09
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/b8db9a07e59282549ca381dbe650bd11

##### 한 줄 요약

공부를 끝내고 결과를 확정합니다. **공부 시간 · 자리 지킴 시간은 프론트가 계산해서 보내고**, 서버는 그 값을 저장해(주간 · 월간 통계용) 호감도 정산과 완료 대사를 돌려줍니다. 이미 끝난 세션이면 같은 결과를 다시 줍니다 (연타 · 자동 마감과 겹쳐도 안전).

> 📏 측정은 **자리에 있다 / 없다**뿐이라 '집중'이 아니라 **'자리 지킴'**으로 표현합니다.

##### 와이어프레임 대조

| 화면 요소 | 값의 출처 |
| --- | --- |
| H07 '공부 끝내기' · 로고 → H17 '응, 여기까지 할게' / H08 '오늘은 여기까지' | 이 API 호출 |
| H09 ‘◷ 00.00 00:00 완료' | 응답 `endedAt` |
| H09 '함께 0시간 00분 공부했어요' | **프론트 계산값** (요청으로 보낸 `studySeconds`) |
| H09 '목표 50분 · 목표 달성 (미달 시 00% 공부했어요)' | **프론트 계산** (`studySeconds` ÷ 목표) |
| H09 원형 ‘00%’ (집중률 → **자리 지킴률**) | **프론트 계산** (`presentSeconds` ÷ `studySeconds`) |
| H09 '집중 0분' (→ **자리 지킴**) · '얼굴 미감지 0분' (→ **자리 비움**) | **프론트 계산값** |
| H09 '복귀 0회' | 응답 `returnCount` |
| H09 '채린 Lv.1 · +00분' 막대 (진한 = 공부 시작 때, 연한 = 이번에 늘어난 만큼), 레벨업 효과 | 응답 `relation` |
| H09 ‘[완료 대사]’ · H10 ‘[카드 문구]’ | 응답 `closingLine` (같은 문장) |
| H09 '대화 옵션 · 스탠다드', 다른 캐릭터 탭 | **제거** / 목업 |

- 테이블: `study_sessions`, `user_character_relations`, `relation_settlements`, `conversation_recaps`, `chat_messages`(카드에 넣은 뒤 삭제), `llm_usages` · 인증: 필요

##### [POST] /api/sessions/{sessionId}/end

##### 🛫 Request

| 이름 | 타입 | 필수 | 설명 | DB 저장 |
| --- | --- | --- | --- | --- |
| studySeconds | Integer | O | 공부 시간 = 세션 시간 − 휴식 · 일시정지 (프론트 타이머 값) | `focus_seconds + distract_seconds` |
| presentSeconds | Integer | O | 자리 지킴 = 공부 시간 − 자리 비움 | `focus_seconds` |

json

```json
{ "studySeconds": 3300, "presentSeconds": 2880 }
```

- 자리 비움(`distract_seconds`)은 서버가 `studySeconds − presentSeconds`로 저장
- 주간 · 월간 통계는 `study_sessions`의 이 값들을 기간별로 더해서 계산

##### 🛬 Response

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| sessionId · roomName |  |  |
| startedAt · endedAt · endType |  | `NORMAL` / `AUTO_CLOSED` |
| studySeconds | Integer | 저장된 공부 시간 |
| presentSeconds | Integer | 저장된 자리 지킴 시간 |
| awaySeconds | Integer | 저장된 자리 비움 시간 (studySeconds − presentSeconds) |
| returnCount | Integer | 자리 비운 뒤 돌아온 횟수 (`RETURN` 이벤트 수) |
| character | Object | `id`, `name` |
| relation | Object | `levelBefore` · `levelAfter` · `levelUp`(공부 시간 레벨, 이 세션 자리 지킴 시간 반영) · `nextLevelSeconds`(다음 레벨이 되는 누적 자리 지킴 시간) · `pointsBefore`(공부 시작 때) · `pointsAfter`(이벤트 · 대화 반영 후) · `stage` · `stageName`(끝날 때 관계 단계) · `levelName`(옛 필드, `stageName`과 같은 값) · `nextLevelPoints`(옛 필드, 늘 null) |
| closingLine | String | 완료 대사 (리포트 · 카드 공용). 캐릭터 설정에 완료 대사(`character_lines` CLOSING)가 있으면 지금 관계 단계의 그 문장(PM: 말포이는 고정. 단계별 문장은 확정 필요), 없으면 AI. 직접 끝냈는데 목표 시간의 절반도 못 채웠으면 공부 포기 대사(GIVE_UP, 임시 J-15, 문구 확정 필요). 기준 시간은 공부 시간 `studySeconds`(자리 지킴 + 자리 비움, 쉬는 시간 제외)로 확정, 목표 시간을 정하지 않은 세션은 포기로 보지 않음 (절반 · 직접 끝냄 조건은 확정 필요) |

> 2026-11-04 (호감도 최종안): 공부 시간은 호감도를 올리지 않고 레벨에만 씀. 레벨 = 자리 지킴 60분당 1 (임시, PM 확인 필요)

json

```json
{  "sessionId": 512, "roomName": "밤의 호그와트 도서관",  "startedAt": "2026-11-07T05:00:02Z", "endedAt": "2026-11-07T06:05:10Z", "endType": "NORMAL",  "studySeconds": 3300, "presentSeconds": 2880, "awaySeconds": 420,  "returnCount": 2,  "character": { "id": 1, "name": "채린" },  "relation": { "levelBefore": 1, "levelAfter": 2, "levelUp": true, "nextLevelSeconds": 7200, "pointsBefore": 50, "pointsAfter": 53, "stage": 2, "stageName": "친한 사이", "levelName": "친한 사이", "nextLevelPoints": null },  "closingLine": "오늘도 같이 공부해서 좋았어. 다음에도 함께하자."}
```

> ✅ **200 OK** (이미 끝난 세션도 200 + 같은 결과)

> ❌ **400** INVALID_STUDY_TIME (음수 · `presentSeconds > studySeconds` · 실제 경과 시간 초과) · **404** SESSION_NOT_FOUND

##### 서버가 하는 일 (한 트랜잭션)

1. `ended_at IS NULL`일 때만 종료 (두 번 눌러도 한 번만 정산)
2. 받은 값 검증: `0 ≤ presentSeconds ≤ studySeconds ≤ (종료 시각 − 시작 시각)` → 통과하면 `focus_seconds` · `distract_seconds` 저장, `RETURN` 이벤트 수로 `return_count` 저장
3. 함께한 시간 누적(`total_study_seconds` · `total_present_seconds`) → 레벨 갱신, `relation_settlements`에 이 세션 레벨 전후 저장 (호감도는 바꾸지 않음)
4. 이번 세션 대화를 흐름 카드 한 장으로 써서 `conversation_recaps`에 저장(최근 2장만 남김)하고 `chat_messages` 삭제. 카드 앞에 날짜 · 공부 시간을 붙임 (예: "11/03 55분, …")
5. 완료 대사(`closingLine`)를 만들어 `study_sessions.closing_line`에 저장
6. LLM이 실패하거나 5초를 넘겨도 종료는 성공: 카드를 못 쓰면 메시지를 지우지 않고 남김 → 다음 종료 때 그 세션 카드에 같이 들어감. 완료 대사는 고정 문구

---

##### 같이 챙길 것 2가지

- **리포트 조회(새로고침)는 응답에 시간 값을 다시 넣어야 함**: 새로고침하면 프론트 계산값이 사라지니까, 리포트 조회는 저장된 `studySeconds` · `presentSeconds` · `awaySeconds`를 돌려줘야 H09를 다시 그릴 수 있음 → 2026-11-03 결정: 공부 종료 · 리포트 응답에 넣음
- **자동 마감 · 비정상 종료는 프론트 값이 없음**: 6시간 방치로 배치가 닫을 땐 보내줄 프론트가 없어서, 그때는 **서버가 이벤트로 직접 계산**해야 함. 그래서 이벤트로 계산하는 로직은 서버에 계속 있어야 하고, 위 2번 검증에서도 그 계산값과 크게 차이 나면 서버 값으로 바꾸는 식으로 쓰면 조작도 같이 막을 수 있음

## 23. GET /api/sessions/{sessionId}/report — 리포트 조회
- 분류: 리포트
- 설명: 리포트 · 카드 다시 불러오기 (공부 종료와 같은 응답)
- 와이어프레임: H09 새로고침 · H10 캡슐 카드
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/1c2b9a07e59282c4aa7c8102c2003afb

#### 설명

끝난 세션의 결과를 다시 불러옵니다. 응답은 '공부 종료'와 같습니다.

#### 와이어프레임 대조

| 화면 요소 | 이 API가 하는 일 |
| --- | --- |
| H09 화면에서 새로고침 | 리포트 다시 표시 |
| H09 '캐슐 카드 만들기' → H10 | 카드 날짜 · '채린과 함께한 공부 0시간 00분' · [자리 지킴 문구] (자리 지킴률 기준) · [카드 문구] |
| 자동 마감된 세션 (이어하기 409) | 마감된 결과 보여주기 |
| H10 '이미지 저장' · '스토리 공유' · '링크 복사' · '카드 색' | 프론트 처리 (API 없음) |
| H09 '홈으로' · H10 '저장하지 않고 홈으로' | H03으로 이동 |

- 테이블: `study_sessions`, `relation_logs`, `relation_settlements` · 인증: 필요

#### [GET] /api/sessions/{sessionId}/report

응답: '공부 종료'와 같음

> ✅ **200 OK**

> ❌ **404** SESSION_NOT_FOUND · **409** SESSION_IN_PROGRESS

> ⚠️ 새로고침해도 같은 `closingLine`이 나오려면 저장 자리가 필요함. DB 최종본에는 없으므로 `study_sessions.closing_line` 추가하기로 함 (2026-11-03 결정, 노션 DB 반영). 공부 종료 때 저장하고, 리포트 조회는 저장된 문장을 그대로 돌려줌

## 24. POST /api/auth/logout — 로그아웃
- 분류: 인증
- 설명: 리프레시 토큰 폐기. 진행 중 세션은 유지 (재로그인 시 이어하기)
- 와이어프레임: H03 프로필 메뉴 로그아웃
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/d91b9a07e5928331ba210199ebe8c636

#### 설명

로그아웃합니다. Redis의 리프레시 토큰을 지우고 쿠키를 비웁니다. 진행 중인 세션은 끝내지 않아서, 다시 로그인하면 H18로 이어갈 수 있습니다.

#### 와이어프레임 대조

| 화면 요소 | 이 API가 하는 일 |
| --- | --- |
| H03 헤더 프로필 메뉴 '로그아웃' | 로그아웃 → H01 |
| H03 메뉴 '마이페이지' · '첫 방문 화면 보기' · '체험 기록 초기화' | 목업 |

- 저장: Redis · 인증: 리프레시 토큰 쿠키만 (액세스 토큰은 보지 않음. 2026-11-03: 액세스 토큰이 만료돼도 리프레시 토큰을 폐기해야 공용 시연 기기에서 다음 사람이 이전 계정으로 갱신되지 않음)

#### [POST] /api/auth/logout

> ✅ **204 No Content** · 쿠키가 없거나 이미 폐기된 토큰이어도 204 (항상 쿠키를 비움) → 프론트는 H01로

## 25. POST /api/auth/password/reset-request — 비밀번호 재설정 메일 요청
- 분류: 인증
- 설명: 이메일로 비밀번호 재설정 링크 발송. 가입 여부와 상관없이 같은 응답
- 와이어프레임: H01 로그인 폼 '비밀번호 찾기' (추가 필요)
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/40eb9a07e5928130ad08d117dd508bbc

> 2026-11-04: 비밀번호 찾기 추가 (25 · 26). 메일 링크 방식, 가입하지 않은 이메일도 같은 응답, 카카오 전용 계정은 대상 아님, 바꾸면 모든 기기 로그아웃

#### 설명

비밀번호를 잊은 사용자가 이메일을 넣으면, 그 주소로 비밀번호 재설정 링크를 보냅니다. 링크를 누르면 프론트의 '새 비밀번호' 페이지가 열리고, 그 페이지가 '비밀번호 재설정' API를 부릅니다.

다른 사람이 어떤 이메일이 가입했는지 알아낼 수 없도록, 가입하지 않은 이메일이나 카카오로만 가입한 계정(비밀번호 없음)이어도 같은 202를 줍니다. 이때는 메일을 보내지 않습니다.

#### 와이어프레임 대조

| 화면 요소 | 이 API가 하는 일 |
| --- | --- |
| H01 로그인 폼 '비밀번호 찾기' (추가 필요) | 이메일 입력 화면으로 |
| '재설정 메일 보내기' 버튼 | 이 API 호출 → "메일을 보냈어요. 메일함을 확인해 주세요" (가입 여부와 상관없이 같은 문구) |

> ⚠️ 현재 와이어프레임에 '비밀번호 찾기' 진입점과 이메일 입력 화면, '새 비밀번호' 페이지가 없음. 추가 필요

- 저장: Redis (재설정 토큰) · 인증: 불필요

#### [POST] /api/auth/password/reset-request

#### 🛫 Request

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| email | String | O | 가입한 이메일 (서버가 소문자로 바꿈) |

```json
{ "email": "name@example.com" }
```

#### 🛬 Response

> ✅ **202 Accepted** · 본문 없음. 가입하지 않은 이메일 · 카카오 전용 계정 · 탈퇴한 계정이어도 202 (메일은 안 보냄)

> ❌ **400** INVALID_EMAIL · **429** TOO_MANY_REQUESTS (같은 이메일 1분에 1번)

#### 서버가 하는 일

1. 이메일 형식 검사, 같은 이메일 1분 1번 제한
2. 비밀번호가 있는 활성 계정이면 무작위 재설정 토큰을 만들어 Redis에 보관 (유효시간 30분, 예시 · 확정 필요). 그 사용자의 이전 재설정 토큰은 지움 (마지막 링크만 유효)
3. 메일로 링크 발송: `{프론트 주소}/password/reset?token=<토큰>` (페이지 경로는 프론트와 확정 필요)
4. 계정이 없거나, 비밀번호가 없거나(카카오 전용), 탈퇴한 계정이면 아무것도 하지 않고 같은 202
5. 메일은 응답과 따로 보낸다. 그래서 응답 시간과 결과(발송 실패 포함)로 가입 여부를 알 수 없다. 발송이 실패하면 서버 로그에만 남고 응답은 그대로 202

#### 프론트 참고

- 링크로 열리는 페이지는 주소의 `token`을 읽어 '비밀번호 재설정' API에 그대로 보냄
- 메일이 안 왔을 때를 대비해 스팸함 확인 안내를 같이 보여 주면 좋음

## 26. POST /api/auth/password/reset — 비밀번호 재설정
- 분류: 인증
- 설명: 메일 링크의 토큰과 새 비밀번호로 변경. 바꾸면 모든 기기에서 로그아웃
- 와이어프레임: '새 비밀번호' 페이지 (추가 필요)
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/40eb9a07e592812492d1d69352fcce68

#### 설명

재설정 메일의 링크로 열린 페이지에서 새 비밀번호를 받아 바꿉니다. 토큰은 한 번만 쓸 수 있습니다. 비밀번호가 바뀌면 그 계정의 리프레시 토큰을 모두 무효로 만들어서, 다른 기기도 토큰 갱신이 안 되고 다시 로그인해야 합니다. 이미 받은 액세스 토큰은 만료될 때까지(최대 30분) 그대로 쓰입니다. 이 API는 로그인 토큰을 주지 않으므로, 바꾼 뒤 프론트는 로그인 화면(H01)으로 보냅니다.

#### 와이어프레임 대조

| 화면 요소 | 이 API가 하는 일 |
| --- | --- |
| '새 비밀번호' (8자 이상) | `newPassword` |
| '새 비밀번호 확인' | 프론트에서만 일치 검사, 서버로 안 보냄 |
| '비밀번호 바꾸기' 버튼 | 변경 → "비밀번호를 바꿨어요. 다시 로그인해 주세요" → H01 |

- 저장: `users` (bcrypt 해시), Redis (재설정 토큰 · 리프레시 토큰 폐기) · 인증: 불필요

#### [POST] /api/auth/password/reset

#### 🛫 Request

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| token | String | O | 재설정 링크 주소의 `token` 값 |
| newPassword | String | O | 회원가입과 같은 규칙: 8자 이상, 72바이트 이하. 벗어나면 INVALID_PASSWORD |

```json
{ "token": "pr_3kX9...", "newPassword": "newpass1234" }
```

#### 🛬 Response

> ✅ **204 No Content** → 프론트는 H01(로그인)로

> ❌ **400** INVALID_PASSWORD · RESET_TOKEN_INVALID (만료됐거나 이미 썼거나 없는 토큰, 더 새 링크를 받은 경우, 그사이 탈퇴한 계정. "링크가 만료됐어요. 다시 요청해 주세요")

#### 서버가 하는 일

1. 비밀번호 규칙 검사 (틀리면 토큰은 쓰지 않은 채로 400. 다시 입력 가능)
2. 토큰을 꺼내면서 지움 (한 번만 사용). 없으면 400 RESET_TOKEN_INVALID
3. 비밀번호를 bcrypt로 바꾸고, 로그인 실패 잠금을 풀고, 그 사용자의 리프레시 토큰을 모두 폐기

## 27. DELETE /api/me — 회원 탈퇴
- 분류: 인증
- 설명: 소프트 삭제. 개인정보를 비우고 대화 기억 · 일정을 지운 뒤 모든 기기 로그아웃. 진행 중 세션이 있으면 거절
- 와이어프레임: 마이페이지 '회원 탈퇴' (추가 필요)
- 백엔드: Not started
- 프론트: Not started
- 노션: https://app.notion.com/p/40fb9a07e592813bb828d6221ebd26ca

> 2026-11-05: 회원 탈퇴 추가. DB 최종본의 탈퇴 원칙(소프트 삭제, `users` 행은 남기고 개인정보만 비움)을 API로

#### 설명

탈퇴합니다. `users` 행은 남기고(공부 기록 · 호감도 기록이 이 행을 가리킴) 이메일 · 비밀번호 · 닉네임을 비웁니다. 사용자가 직접 말한 내용(일정, 캐릭터와의 대화 기억)은 지웁니다. 같은 이메일이나 같은 카카오 계정으로 다시 가입하면 새 계정이 되고, 이전 기록은 이어지지 않습니다.

리프레시 토큰을 모두 폐기해서 다른 기기도 토큰 갱신이 안 됩니다. 이미 받은 액세스 토큰은 만료될 때까지(최대 30분) 남지만, 탈퇴한 계정이라 내 정보 조회 등은 401이 됩니다. 프론트는 갖고 있던 토큰을 버리고 H01로 보냅니다.

#### 와이어프레임 대조

| 화면 요소 | 이 API가 하는 일 |
| --- | --- |
| 마이페이지 '회원 탈퇴' (추가 필요) | 확인 창("대화 기억과 일정이 모두 지워져요") → 이 API → H01 |

> ⚠️ 현재 와이어프레임에 탈퇴 진입점과 확인 창이 없음 (H03 '마이페이지'가 목업). 추가 필요

- 저장: `users` (개인정보 비움 · `withdrawn_at`), `user_social_accounts` · `user_memories` · `conversations`(+ `chat_messages` · `conversation_recaps`) · `character_memories` 삭제, Redis (리프레시 토큰 폐기) · 인증: 필요

#### [DELETE] /api/me

본문 없음.

> ⚠️ 비밀번호 다시 입력 · 탈퇴 사유 수집은 하지 않음 (확정 필요)

#### 🛬 Response

> ✅ **204 No Content** · 리프레시 토큰 쿠키를 비움 → 프론트는 토큰을 버리고 H01로. 이미 탈퇴한 계정이어도 204

> ❌ **409** SESSION_IN_PROGRESS (진행 중 세션이 있음. "공부를 끝낸 뒤 탈퇴할 수 있어요" → 세션 이어하기 후 종료)

#### 서버가 하는 일 (한 트랜잭션)

1. 진행 중 세션이 있으면 409. 세션이 남아 있으면 자동 마감이 탈퇴 뒤에 정산 · 대화 카드를 다시 만들기 때문
2. `users`: `withdrawn_at = now()`, `email` · `password` NULL, `nickname`은 '탈퇴한 사용자'(NOT NULL이라 고정 문구), `avatar_id` · `profile_image_url` NULL
3. `user_social_accounts` 행 삭제 (같은 소셜 계정으로 재가입 가능)
4. `user_memories` 삭제, `conversations`(대화 원문 · 세션 카드는 CASCADE) · `character_memories` 삭제
5. 남기는 것: `user_terms`(동의 기록), `study_sessions` · `session_events`, `user_character_relations` · `relation_logs` · `relation_settlements` · `user_unlocked_*`, `llm_usages`. 모두 개인을 알아볼 내용이 없는 기록
6. 커밋 뒤 그 사용자의 리프레시 토큰을 모두 폐기 (비밀번호 재설정과 같은 방식)

- 카카오 연결 끊기(카카오 unlink API)는 하지 않음. 카카오 토큰을 저장하지 않아서 서버가 부를 수 없음 (어드민 키 방식으로 할지 확정 필요)
- 재가입 제한 기간 없음 (확정 필요)
