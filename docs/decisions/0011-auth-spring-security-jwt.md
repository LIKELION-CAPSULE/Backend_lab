# 0011 인증: Spring Security + JWT(리소스 서버 스타터), 리프레시 토큰은 Redis

- 결정: 액세스 토큰은 HS256 JWT(30분, `sub` = 사용자 id). 발급과 검증은 Spring Security + `spring-boot-starter-oauth2-resource-server`(Nimbus 포함)로 하고, 비밀번호는 bcrypt(`spring-security-crypto`)로 저장한다. 리프레시 토큰은 무작위 문자열을 Redis에 저장하고(유효기간은 설정값, 기본 14일) 쓸 때마다 교체한다. 전달은 HttpOnly 쿠키로, 로그아웃은 Redis에서 삭제한다.
- 이유: 명세(API 4·5·6·7·24)가 "액세스 30분 + 리프레시 HttpOnly 쿠키, 쓸 때마다 교체"다. 스타터 하나에 JWT 검증 필터, 401 처리, bcrypt가 다 들어 있어서 직접 만드는 코드가 적다. 리프레시를 Redis의 무작위 값으로 두면 교체와 폐기가 키 하나 지우는 것으로 끝난다(0009).
- 버린 대안: jjwt + 직접 만든 필터(의존성이 하나 더 들고 필터를 우리가 유지보수함), 세션 쿠키 인증(명세와 다름), 리프레시도 JWT로(폐기하려면 블랙리스트가 또 필요함).
- 같이 정한 것: 외부 연동(카카오 로그인, 인증 메일)은 포트 + Fake로 시작하고, 실제 어댑터는 키를 받은 뒤 `RestClient`로 붙인다(의존성 추가 없음). E2E는 MockMvc로 한다. 자동 마감 배치는 `@Scheduled`로 하며 서버 1대가 전제다. 여러 대가 되면 ShedLock 같은 락을 검토한다.
- 비밀값: JWT 서명 키는 환경변수(로컬은 `.env.local`, 배포는 시크릿)로 받는다. 레포에는 두지 않는다.
