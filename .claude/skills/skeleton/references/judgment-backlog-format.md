# judgment-backlog.md 형식

뼈대 단계에서 "자리만 만든" 판단 영역의 목록. 도메인 단계에서 각자 자기 항목을 가져간다.

```markdown
# 판단 백로그

| ID | 모듈 | 담당 | 무엇이 미정인가 | 코드 위치 | 지금 Fake 동작 | 근거 명세 |
|---|---|---|---|---|---|---|
| J-01 | dialogue | seunghee | 이벤트 반응 대사 생성 | dialogue/internal/client/FakeLlmClient.java | 고정 문장 반환 | api.md 19 |
| J-02 | relation | seunghee | 공부 시간·이벤트 → 호감도 증가 공식 | relation/internal/service/AffinityPolicy.java | 이벤트당 +1 | api.md 18, 22 |
```

규칙
- ID는 J-01부터 증가. 코드 주석 `// TODO(judgment): J-01 ...`과 같은 ID를 쓴다.
- 담당은 zones.conf의 모듈 담당을 그대로 쓴다.
- 항목을 구현하는 PR은 본문 '목적'에 ID를 적고, 머지 시 표에서 행을 지우거나 '완료'로 표시한다.
