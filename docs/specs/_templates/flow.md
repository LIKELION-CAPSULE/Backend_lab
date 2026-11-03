# 핵심 흐름

<!-- 이 흐름이 그대로 전체 플로우 E2E 테스트(FlowE2ETest)와 http/flow.http가 된다.
     단계마다: 사용자가 하는 일 → 부르는 API → 기대 결과 -->

## 흐름 1: 첫 공부 세션
1. 로그인 → `POST /api/auth/login` → 토큰 발급
2. 스터디룸 입장 → `POST /api/rooms/{id}/join` → 200
3. 세션 시작 → `POST /api/sessions` → sessionId
4. 딴짓 감지 → `POST /api/sessions/{id}/events` (DISTRACTED) → 202
5. 캐릭터 반응 → `POST /api/dialogue/reply` → 대사 1개 (뼈대 단계에선 Fake 대사)
6. 세션 종료 → `POST /api/sessions/{id}/end` → 공부 시간 기록
7. 호감도 확인 → `GET /api/characters/{id}/affinity` → 0보다 큼 (뼈대 단계 공식: 세션당 +1)
