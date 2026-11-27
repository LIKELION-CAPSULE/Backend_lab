# 핵심 흐름

<!-- 이 흐름이 그대로 전체 플로우 E2E 테스트(FlowE2ETest)와 http/flow.http가 된다.
     단계마다: 사용자가 하는 일 → 부르는 API → 기대 결과. 화면 이름은 PRD(docs/product/prd-beta.md) 6장.
     2026-11-28: 12/1 베타(캡슐 메이트 · 연결) 흐름으로 다시 씀. 스터디 시절 흐름(룸 · 공부 세션 · 일정)은 git 이력에. -->

## 흐름 1: 이메일 가입부터 연결 기록까지
1. 약관 보기 (회원가입) → `GET /api/terms` → 필수 약관 2개, `termVersionId`
2. 인증번호 요청 → `POST /api/auth/email/verification` → 202 (로컬·테스트는 Fake 메일이 번호를 들고 있음)
3. 인증번호 입력 → `POST /api/auth/email/verification/confirm` → `verificationToken`
4. 가입 (닉네임 · 생년월일) → `POST /api/auth/email/signup` → 201, `onboardingRequired: true`, 리프레시 쿠키
5. 아바타 보기 (아바타 선택) → `GET /api/avatars`
6. 이 모습으로 시작 → `PATCH /api/me` (아바타만, 닉네임은 가입 때 값) → `onboardingRequired: false`
7. 홈 → `GET /api/home` → 메이트 카드 목록, `activeConnection` · `lastMate` null, 오늘 함께한 시간 0
8. 메이트 상세 → `GET /api/mates/1` → 모델(CUBISM), `hasConversation: false`
9. 일정 저장(13)은 지워졌다 → `POST /api/me/memories` 404
10. 연결하기 → `POST /api/connections` → 준비 중 메이트(2)는 409 `MATE_COMING_SOON`, 메이트 1은 201: `connectionId`, 첫 만남 인사(`greeting`, 표정 '웃음'), 오늘 출석 크레딧 1,500
11. 얼굴 감지 시작 → `PRESENT` 전송 → 반응 없음 (19번도 부르지 않음)
12. 1분마다 → heartbeat (연결 · 함께한 · 카메라 켠 · PIP 시간) → 청소년이 아니라 `minorRemainingSeconds: null`
13. 자리 비움 → `AWAY` 전송 → 반응(19, `intensity: 1`) → 같은 이벤트를 다시 보내면 같은 `eventId` (다시 반영하지 않음)
14. 복귀 → `RETURN` → 반응. 말 걸기 타이머 → `IDLE_TALK` → 반응(trigger `IDLE`)
15. 채팅 → 메시지 전송 → Fake 답장. 같은 `requestId`로 다시 보내면 한 번만 처리
16. 새로고침 → `GET /api/me` → `activeConnection` 있음 (이어하기 팝업 근거)
17. 연결 끊기 → `POST /api/connections/{id}/end` → 연결 시간 · 함께한 시간, 친밀도(대화가 있어 함께한 1분 그대로 인정, 1단계 그대로 · 다음 단계까지 남은 시간), 완료 대사, 대화 수 1
18. 끊기 연타 → 같은 결과 (멱등)
19. 대화방 기록 → `GET /api/mates/1/messages` → 끊어도 메시지가 남음, 커서로 쪽 넘김
20. 연결 기록 새로고침 → `GET /api/connections/{id}` → 17과 같은 결과. 홈에 오늘 함께한 시간 · `lastMate`
21. 두 번째 연결의 완료 대사는 다음 줄 (사용자 단위로 돌려 씀)
22. 토큰 만료 → `POST /api/auth/refresh` → 새 토큰. 옛 리프레시는 401
23. 로그아웃 → 204 (액세스 토큰이 없어도 리프레시를 폐기)

## 흐름 2: 카카오 신규 가입 (로컬·테스트는 Fake 카카오: 코드 `fake-kakao:{회원번호}:{닉네임}`)
카카오 로그인(6) → 새 사용자면 403 + 가입 토큰 → 약관 동의 → 카카오 가입 완료(49) → 같은 카카오 계정으로 다시 6 → 200

## 흐름 3: 비정상 종료 후 이어하기
카카오 가입 → 온보딩에서 생년월일 입력(없으면 연결이 막힘) → 연결 → 7분째 heartbeat → (탭 닫힘) → 이어하기(17) → 마지막 heartbeat 값으로 타이머를 이음(끊긴 시간은 안 셈) → 끊기
