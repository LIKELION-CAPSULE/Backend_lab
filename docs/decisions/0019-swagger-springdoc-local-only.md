# 0019 Swagger는 springdoc, local 프로필(로컬 · 개발 서버)에서만 (0013 보완)

- 결정
  - `springdoc-openapi-starter-webmvc-ui` 3.1.1(Boot 4.1 기준 빌드)로 `/swagger-ui.html`과 `/v3/api-docs`를 만든다
  - 공용 설정에서 끄고(`springdoc.*.enabled: false`) local 프로필에서만 켠다. 개발 서버(`local,shared-db,dev-server`, 0018)에서도 켜지고, 운영(`prod`)에서는 꺼져서 404
  - Swagger 경로 3개(`/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`)는 로그인 없이 연다. 꺼진 환경에서는 응답할 것이 없다
  - Authorize 버튼(Bearer JWT)에 액세스 토큰을 넣으면 로그인이 필요한 API도 부를 수 있다(`common/config/OpenApiConfig`)
- 이유
  - 프론트가 개발 서버에서 실제 요청 · 응답 모양을 보고 바로 눌러 볼 수 있다(2026-11-04 요청)
  - 명세 원본은 그대로 `docs/specs/api.md`다(0013). Swagger는 "지금 코드가 실제로 어떤지" 보는 화면이고, 0013에서 OpenAPI를 구현 기록으로 쓰기로 한 것과 맞다
  - 운영에서는 API 목록을 공개할 이유가 없다
- 버린 대안
  - Spring REST Docs: 문서마다 테스트를 써야 해서 지금 단계엔 품이 크다
  - 운영에서도 켜기: API 목록이 공개된다
  - springdoc 3.2.0: Boot 4.1.2 기준이지만 나온 지 2주가 안 됐다. 3.1.1이 문제를 보이면 올린다
- 대가
  - 응답 예시 · 설명은 애너테이션을 달기 전까지 비어 있다. 필드 의미는 api.md를 본다
  - springdoc(swagger-core)이 Jackson 2를 같이 들여온다(앱의 JSON 변환은 Jackson 3 그대로). Boot BOM이 swagger-core가 원하는 2.22보다 낮은 2.21로 맞춘다. 문서 생성에서 `NoSuchMethodError` 같은 오류가 나면 springdoc을 올리는 것부터 본다
  - 서버 주소는 상대 경로(`/`)로 고정했다. 프록시 뒤에서 http 주소가 캐시되는 문제를 피하려는 것
  - Swagger와 api.md가 다르면 코드가 명세와 어긋난 것이다. 명세가 맞으면 코드를, 코드가 맞으면 명세 PR을 고친다(0015)
