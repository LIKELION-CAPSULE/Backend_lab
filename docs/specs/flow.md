# 핵심 흐름

<!-- 이 흐름이 그대로 전체 플로우 E2E 테스트(FlowE2ETest)와 http/flow.http가 된다.
     단계마다: 사용자가 하는 일 → 부르는 API → 기대 결과. 와이어프레임 H01 → H10 순서.
     2026-11-03 skeleton-plan 5장에서 확정. -->

## 흐름 1: 이메일 가입부터 리포트까지
1. 약관 보기 (H01) → `GET /api/terms` → 약관 2개, `termVersionId`
2. 인증번호 요청 → `POST /api/auth/email/verification` → 202 (로컬·테스트는 Fake 메일이 번호를 들고 있음)
3. 인증번호 입력 → `POST /api/auth/email/verification/confirm` → `verificationToken`
4. 가입 (닉네임 포함) → `POST /api/auth/email/signup` → 201, `onboardingRequired: true`, 리프레시 쿠키
5. 아바타 보기 (H02) → `GET /api/avatars` → 1개
6. 이 모습으로 시작 → `PATCH /api/me` → `onboardingRequired: false`
7. 홈 (H03) → `GET /api/home` → 룸 1개, `activeSession: null`
8. 룸 상세 (H04) → `GET /api/rooms/1` → 채린, Lv.1 · 0초
9. 시험 일정 (H06) → `POST /api/me/memories` (EXAM) → 201, `dDay`
10. 공부 시작 (H06 → H07) → `POST /api/sessions` → 201, `sessionId`
11. 시작 직후 → `FOCUS` 전송 → `eventId`, `characters[].affinity` +5 (FIRST_MEET, J-01 Fake) → 반응(19) → 인사 대사
12. 1분마다 → heartbeat (지금까지의 `studySeconds` · `presentSeconds`) → 204
13. 자리 비움 (H07) → `AWAY` 전송 → `characters[].affinity` −1 → 반응(`intensity: 1`) → 대사
14. 같은 `AWAY`를 다시 보냄 → 200 + 기존 응답 (같은 `eventId`, `delta`도 처음과 같은 −1. 점수는 다시 깎이지 않음)
15. 복귀 → `RETURN` 전송 → `characters[].affinity` +1 → 반응
16. 채팅 (H20) → 메시지 전송 → Fake 답장. 같은 `requestId`로 다시 보내면 한 번만 처리
17. 새로고침 → `GET /api/me` → `activeSession` 있음 (H18 근거)
18. 공부 끝내기 (H17) → `POST .../end` → `returnCount: 1`, `pointsAfter > pointsBefore`, `closingLine`
19. 종료 연타 → 같은 결과 (멱등)
20. 리포트 새로고침 (H09) → `GET .../report` → 18과 같은 결과
21. 토큰 만료 → `POST /api/auth/refresh` → 새 토큰. 옛 리프레시는 401
22. 로그아웃 → 204

## 흐름 2: 카카오 신규 가입 (로컬·테스트는 Fake 카카오: 코드 `fake-kakao:{회원번호}:{닉네임}`)
카카오 로그인(약관 없이) → `isNewUser: true`, 닉네임 = 카카오 닉네임 → 같은 카카오 계정으로 다시 → `isNewUser: false`

## 흐름 3: 비정상 종료 후 이어하기 (H18)
공부 시작 → FOCUS → heartbeat → (탭 닫힘) → 이어하기 → `breakSeconds > 0`, 끊긴 구간은 공부 시간에 안 들어감 → 종료
