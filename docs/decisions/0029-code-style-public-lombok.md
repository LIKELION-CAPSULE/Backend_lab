# 0029 코드 스타일: public 기본 + Lombok

- 결정
  - main 코드의 클래스 · 메서드 · 생성자는 `public`을 기본으로 쓴다(흔한 Spring 레포 모양). 필드는 `private`
  - 생성자 주입은 `@RequiredArgsConstructor`. 생성자 안에서 값을 가공하거나 `@Value` 파라미터를 받는 클래스는 생성자를 직접 쓴다
  - 엔티티: `private` 필드 + `@Getter`(`getId()` 형식), JPA 기본 생성자는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`. 값 변경은 이름 있는 메서드(`changeNickname` 등)로만, `@Setter` · `@Data` 금지(`lombok.config`에서 막음)
  - DTO · 계약 타입은 지금처럼 `record`
  - 테스트 코드는 JUnit 5 관례대로 접근 제어자를 생략한다(바꾸지 않음)
  - 모듈 경계는 그대로 Spring Modulith(ADR 0001)가 지킨다. `public`이어도 다른 모듈이 레이어 패키지를 쓰면 `ModularityTests`가 실패한다
- 이유
  - 처음 뼈대는 최소 공개(접근 제어자 생략) + 직접 쓴 생성자 + record식 getter(`id()`)였다. 둘이 쓰던 크루윙 등 익숙한 레포와 모양이 달라 읽을 때 헷갈렸다(2026-11-11 승희 · 성욱 합의)
  - Lombok으로 생성자 · getter 반복 코드를 없앤다
- 버린 대안
  - 지금 스타일 유지 + 문서화: 경계는 컴파일러가 하나 더 막지만, 팀이 읽기 어렵다
  - `public`만 붙이고 Lombok 없이: 익숙한 모양의 절반만 얻는다
- 대가
  - 컨트롤러 · adapter를 같은 모듈의 다른 패키지에서 import하는 실수를 컴파일러가 막지 못한다(다른 모듈에서 쓰는 건 Modulith가 막음)
  - 엔티티 getter 이름이 바뀌어(`id()` → `getId()`) 한 번에 바꾸는 큰 PR이 하나 생긴다
