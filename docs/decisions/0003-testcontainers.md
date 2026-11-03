# 0003 Testcontainers (H2 사용 안 함)

- 결정: 통합·E2E 테스트는 Testcontainers의 실제 PostgreSQL로.
- 이유: H2는 Flyway SQL과 PostgreSQL 전용 타입에서 동작이 달라 "테스트 통과, 실서버 실패"를 만든다. 에이전트가 이 거짓 통과를 믿고 끝내는 게 가장 위험함.
- 비용: 테스트 시작이 느림 → verify.sh --quick 은 바뀐 모듈 테스트만 돌림.
