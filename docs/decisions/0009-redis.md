# 0009 Redis (토큰·인증번호·로그인 잠금)

- 결정: 리프레시 토큰, 이메일 인증번호(5분 유효), 비밀번호 재설정 토큰, 로그인 잠금은 Redis에 둔다.
- 이유: DB 최종본(2026-11-02)의 원칙. 만료가 있는 짧은 값을 DB 테이블로 두면 정리 작업이 필요하고 테이블만 늘어난다.
- 영향: docker-compose에 redis, 테스트는 Testcontainers GenericContainer(redis:7-alpine).
