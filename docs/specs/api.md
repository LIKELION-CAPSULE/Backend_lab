<!-- 노션 원본 스냅샷. spec-sync 스킬이 만든다. 직접 고치지 말고 노션을 고친 뒤 spec-sync. 출처: docs/specs/SOURCE.md -->
# API 명세 (시연 API 최종본)

> 초기 스냅샷: 노션 DB의 목록(행 속성)만 담겨 있음. 각 행 페이지의 요청·응답·에러·와이어프레임 대조는 Claude Code에서 spec-sync를 처음 실행할 때 아래 각 섹션에 채워짐.

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
| 18 | 공부 세션 | POST | `/api/sessions/{sessionId}/events` | 상태 변화 전송 |
| 19 | 대화 | POST | `/api/sessions/{sessionId}/events/{eventId}/reaction` | 이벤트 반응 받기 |
| 20 | 대화 | POST | `/api/sessions/{sessionId}/messages` | 메시지 전송 |
| 21 | 공부 세션 | POST | `/api/sessions/{sessionId}/heartbeat` | heartbeat |
| 22 | 리포트 | POST | `/api/sessions/{sessionId}/end` | 공부 종료 |
| 23 | 리포트 | GET | `/api/sessions/{sessionId}/report` | 리포트 조회 |
| 24 | 인증 | POST | `/api/auth/logout` | 로그아웃 |

순서 15는 노션에 없음 (번호 비어 있음).

## 1. GET /api/terms — 약관 조회
- 분류: 인증
- 설명: 회원가입 화면에 띄울 필수 약관 2종 최신 버전
- 와이어프레임: H01 회원가입 약관 체크
- 노션: https://app.notion.com/p/abcb9a07e59282988185012651220aa9
- 상세: (spec-sync 실행 전)

## 2. POST /api/auth/email/verification — 이메일 인증번호 요청
- 분류: 인증
- 설명: 가입할 이메일로 6자리 인증번호 발송 (5분 유효)
- 와이어프레임: H01 회원가입 이메일 (인증 칸 추가 필요)
- 노션: https://app.notion.com/p/9e7b9a07e5928202a0a681439fa8dd19
- 상세: (spec-sync 실행 전)

## 3. POST /api/auth/email/verification/confirm — 이메일 인증 확인
- 분류: 인증
- 설명: 인증번호 확인 → 가입용 토큰 발급
- 와이어프레임: H01 회원가입 (인증번호 칸 추가 필요)
- 노션: https://app.notion.com/p/64eb9a07e59283f496a581e09d92a08e
- 상세: (spec-sync 실행 전)

## 4. POST /api/auth/email/signup — 회원가입
- 분류: 인증
- 설명: 이메일 · 비밀번호 · 약관 동의로 가입, 바로 로그인 토큰 발급
- 와이어프레임: H01 회원가입 폼
- 노션: https://app.notion.com/p/2dbb9a07e592820aa64601d3e4aa71aa
- 상세: (spec-sync 실행 전)

## 5. POST /api/auth/email/login — 이메일 로그인
- 분류: 인증
- 설명: 이메일 · 비밀번호 로그인
- 와이어프레임: H01 로그인 폼
- 노션: https://app.notion.com/p/d28b9a07e59282de843a0113489d74c2
- 상세: (spec-sync 실행 전)

## 6. POST /api/auth/social/KAKAO — 카카오 로그인 · 가입
- 분류: 인증
- 설명: 카카오 인가 코드로 로그인, 처음이면 가입까지 (소셜은 카카오만)
- 와이어프레임: H01 카카오로 계속하기
- 노션: https://app.notion.com/p/eafb9a07e59282c19d5d012f95549d8a
- 상세: (spec-sync 실행 전)

## 7. POST /api/auth/refresh — 토큰 갱신
- 분류: 인증
- 설명: 액세스 토큰 만료 시 재발급
- 와이어프레임: 공통
- 노션: https://app.notion.com/p/99db9a07e59283bc909301b2834f24aa
- 상세: (spec-sync 실행 전)

## 8. GET /api/me — 내 정보 조회
- 분류: 온보딩 · 홈
- 설명: 내 정보 + 온보딩 필요 여부 + 진행 중 세션(H18 판단)
- 와이어프레임: 로그인 직후 분기 · H03 헤더 · H18
- 노션: https://app.notion.com/p/b63b9a07e5928365a77c8137046b0b34
- 상세: (spec-sync 실행 전)

## 9. GET /api/avatars — 아바타 목록 조회
- 분류: 온보딩 · 홈
- 설명: 완성형 Live2D 아바타 목록 (현재 1개)
- 와이어프레임: H02 아바타 미리보기
- 노션: https://app.notion.com/p/fecb9a07e592825eacb1010f79c810ff
- 상세: (spec-sync 실행 전)

## 10. PATCH /api/me — 프로필 설정
- 분류: 온보딩 · 홈
- 설명: 닉네임 · 아바타 저장 (온보딩 완료)
- 와이어프레임: H02 이 모습으로 시작하기 · H14 프로필 저장
- 노션: https://app.notion.com/p/d08b9a07e5928331b54881fec20dc4be
- 상세: (spec-sync 실행 전)

## 11. GET /api/home — 홈 조회
- 분류: 온보딩 · 홈
- 설명: 오늘 공부 시간 · 이어하기 · 들어갈 수 있는 룸 1개
- 와이어프레임: H03 홈
- 노션: https://app.notion.com/p/f66b9a07e592825d98d1015154dd556d
- 상세: (spec-sync 실행 전)

## 12. GET /api/rooms/{roomId} — 스터디룸 상세 조회
- 분류: 온보딩 · 홈
- 설명: 룸 정보 + 캐릭터 1명(성격 태그 배열) + 나와의 관계(함께 n분 · Lv)
- 와이어프레임: H04 스터디룸 상세
- 노션: https://app.notion.com/p/f7eb9a07e59282779f7881792cb5f282
- 상세: (spec-sync 실행 전)

## 13. POST /api/me/memories — 일정 저장
- 분류: 온보딩 · 홈
- 설명: 시험 · 약속 · 목표 일정 저장
- 와이어프레임: H06 시험 · H10 다음 공부 약속 · H14 일정 추가
- 노션: https://app.notion.com/p/6cab9a07e59282108b0081090687fbf3
- 상세: (spec-sync 실행 전)

## 14. GET /api/me/memories — 내 일정 조회
- 분류: 온보딩 · 홈
- 설명: 내 일정(시험 · 약속 · 목표) 조회 — 달력 점 · 날짜 클릭 · 예정/전체/지난 탭
- 와이어프레임: H14 마이페이지 달력 · 일정 목록
- 노션: https://app.notion.com/p/da5b9a07e59283ce98c401775309fcb9
- 상세: (spec-sync 실행 전)

## 16. POST /api/sessions — 공부 시작
- 분류: 공부 세션
- 설명: 룸 · 캐릭터 1명 · 목표 시간으로 세션 생성 + 캐릭터 · 내 프로필 이미지
- 와이어프레임: H06 시작하기 → H07
- 노션: https://app.notion.com/p/515b9a07e59282da86b901b17c7c86fc
- 상세: (spec-sync 실행 전)

## 17. POST /api/sessions/{sessionId}/resume — 세션 이어하기
- 분류: 공부 세션
- 설명: 비정상 종료된 세션 이어하기. 끊긴 구간은 자동 휴식 처리
- 와이어프레임: H18 이어서 시작할게 · H04 진행 중인 공부로
- 노션: https://app.notion.com/p/93ab9a07e59283a19ef981931ec863cf
- 상세: (spec-sync 실행 전)

## 18. POST /api/sessions/{sessionId}/events — 상태 변화 전송
- 분류: 공부 세션
- 설명: 상태가 바뀔 때마다 1건씩 전송 → 저장 + 호감도 반영 → eventId
- 와이어프레임: H07 자리 감지 · H08 쉬는 시간 · H19 일시정지
- 노션: https://app.notion.com/p/7d3b9a07e59282318ed48104d03bd4c9
- 상세: (spec-sync 실행 전)

## 19. POST /api/sessions/{sessionId}/events/{eventId}/reaction — 이벤트 반응 받기
- 분류: 대화
- 설명: eventId로 캐릭터 반응(대사 + 모션 + 표정) + 바뀐 호감도 받기
- 와이어프레임: H07 말풍선 · 캐릭터 모션 · 표정
- 노션: https://app.notion.com/p/3d4b9a07e59282279b90814c77fa1129
- 상세: (spec-sync 실행 전)

## 20. POST /api/sessions/{sessionId}/messages — 메시지 전송
- 분류: 대화
- 설명: 대화 → 답장 + 모션 · 표정 + 바뀐 호감도 (TTS 선택)
- 와이어프레임: H08 휴식 대화 · H20 공부 중 채팅
- 노션: https://app.notion.com/p/62bb9a07e592830dba5701795f33ec24
- 상세: (spec-sync 실행 전)

## 21. POST /api/sessions/{sessionId}/heartbeat — heartbeat
- 분류: 공부 세션
- 설명: 1분마다 생존 신호. 비정상 종료 판단 기준
- 와이어프레임: H07 · H08 · H19 동안
- 노션: https://app.notion.com/p/e6db9a07e59283b48e8681b64ccebdfb
- 상세: (spec-sync 실행 전)

## 22. POST /api/sessions/{sessionId}/end — 공부 종료
- 분류: 리포트
- 설명: 공부 종료 + 정산 + 관계 갱신 → 공부 시간 · 자리 지킴률 · 달성률 · 호감도 변화
- 와이어프레임: H17 · H08 오늘은 여기까지 → H09
- 노션: https://app.notion.com/p/b8db9a07e59282549ca381dbe650bd11
- 상세: (spec-sync 실행 전)

## 23. GET /api/sessions/{sessionId}/report — 리포트 조회
- 분류: 리포트
- 설명: 리포트 · 카드 다시 불러오기 (공부 종료와 같은 응답)
- 와이어프레임: H09 새로고침 · H10 캡슐 카드
- 노션: https://app.notion.com/p/1c2b9a07e59282c4aa7c8102c2003afb
- 상세: (spec-sync 실행 전)

## 24. POST /api/auth/logout — 로그아웃
- 분류: 인증
- 설명: 리프레시 토큰 폐기. 진행 중 세션은 유지 (재로그인 시 이어하기)
- 와이어프레임: H03 프로필 메뉴 로그아웃
- 노션: https://app.notion.com/p/d91b9a07e5928331ba210199ebe8c636
- 상세: (spec-sync 실행 전)
