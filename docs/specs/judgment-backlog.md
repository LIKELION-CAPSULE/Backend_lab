# 판단 백로그

뼈대 단계에서 "자리만 만든" 판단 영역의 목록이다. 지금은 결정적인 Fake가 돌고, 코드에는 `// TODO(judgment): J-xx` 주석이 있다.
도메인 단계에서 담당자가 자기 항목을 가져가 기획과 함께 정한다. 항목마다 GitHub 이슈가 있다(상태 칸의 번호, 전체 보드는 #21). 구현하는 PR은 본문 '연관된 이슈'에 ID와 `Closes #번호`를 적고, 머지되면 이 표의 상태를 '완료'로 바꾼다.
코드 위치는 `src/main/java/com/capsule/` 기준이다.

| ID | 모듈 | 담당 | 무엇이 미정인가 | 코드 위치 | 지금 Fake 동작 | 근거 명세 | 상태 |
|---|---|---|---|---|---|---|---|
| J-01 | relation | seunghee | 상태 이벤트별 호감도 변화량 | `relation/service/AffinityPolicy.java` | 명세 예시값: 처음 만난 날 첫 FOCUS +5(FIRST_MEET), AWAY −1, RETURN +1, BREAK 0 | api.md 18 "예시, 기획 확정 필요" | 미정 (#14) |
| J-02 | relation | seunghee | 공부 시간 → STUDY_TIME 점수 공식 | `relation/service/AffinityPolicy.java` | 공부 1분당 +1 | api.md 22 | 미정 (#14) |
| J-03 | relation | seunghee | 레벨 기준 포인트·레벨 이름 | `relation/service/AffinityPolicy.java` | Lv.1 0점 '아는 사이', Lv.2 100점 '친한 사이', Lv.3 300점 'Lv.3' | api.md 12, 22 | 미정 (#14) |
| J-04 | dialogue | seunghee | 이벤트 반응 대사(LLM)와 trigger별 고정 대사(5초 초과) | `dialogue/client/FakeLlmClient.java`, `LlmClient.java`(고정 대사 표) | trigger별 고정 문장 1개 | api.md 19 | 미정 (#11) |
| J-05 | dialogue | seunghee | 대화 답장 생성 (캐릭터 설정·요약·대화·일정, 답 길이) | `dialogue/client/FakeLlmClient.java`, `LlmClient.java`(대체 대사) | 고정 문장 + 입력 일부 | api.md 20 | 미정 (#11, 휴식 여부 계약 #8) |
| J-06 | dialogue | seunghee | 대화 호감도 판정 (−5~5) | `dialogue/client/FakeLlmClient.java` | 항상 +1 | api.md 20 | 미정 (#11, 점수는 기획 #7) |
| J-07 | dialogue | seunghee | 답장 감정 → 모션·표정 고르는 규칙 | `dialogue/service/DialogueService.java` | 모션·표정 없음 | api.md 20 | 미정 (#11, 데이터 #9) |
| J-08 | dialogue | seunghee | 세션 종료 시 대화 요약 합치기 | `dialogue/client/FakeLlmClient.java` | 기존 요약 + "[세션 N] 메시지 n개" 한 줄 | api.md 22 | 미정 (#12) |
| J-09 | dialogue | seunghee | 완료 대사 `closingLine` 생성, 못 만들 때 대체 문구 | `dialogue/client/FakeLlmClient.java`, `LlmClient.java`(대체 문구) | 캐릭터 이름을 넣은 고정 문구. 실패·5초 초과면 명세 22 예시 문장 | api.md 22·23 | 미정 (#12) |
| J-10 | dialogue | seunghee | TTS `audioBase64` | `dialogue/service/DialogueService.java` | 항상 null | api.md 19·20 | 미정 (#13) |
| J-11 | session | seonguk | 종료 시 프론트 값과 서버 계산값이 "크게" 다를 때의 기준 | `session/service/SessionService.java` `end()` | 명세의 부등식 검증만 하고 프론트 값 사용 | api.md 22 "같이 챙길 것" | 미정 (#20) |

## 판단 항목을 가져갈 때 같이 볼 것
- **J-05 답 길이 규칙**(공부 중 1~2문장, 쉬는 시간 2~4문장)을 적용하려면 dialogue가 "지금 휴식 중인지"를 알아야 한다. 지금 `session.api.SessionQuery`에는 없어서 계약 확장이 필요하다(contract-change 스킬).
- **J-01 같이 정할 것**: 지금은 AWAY 없이 RETURN만 보내도 +1이 된다(상태 이벤트 검증은 순서를 보지 않음). RETURN 연타로 점수를 올릴 수 있으니, "직전이 AWAY일 때만 점수" 같은 규칙이 필요한지 기획과 정한다.
- **J-01 첫 만남 조건**: 명세는 "처음 만난 날 첫 FOCUS만 +5"인데, 지금은 "이 캐릭터와 FIRST_MEET 기록이 아직 없으면 세션 첫 FOCUS"에 준다. 첫 세션을 FOCUS 없이 끝내면 다른 날 첫 FOCUS가 +5를 받는다. 날짜 기준이 필요한지 같이 정한다.
- **실제 LLM을 붙일 때**: 메시지 전송과 공부 종료는 각각 한 트랜잭션이라 LLM을 기다리는 동안(호출마다 최대 5초) DB 커넥션을 잡고 있다. 공부 종료의 요약이 실패하면 기존 요약을 두고 그 세션 메시지를 지우지 않는다(다시 합치는 배치는 없음).

## 판단은 아니지만 남은 일
이슈: 실제 시드 #19, 카카오 #17, 메일 #18, LLM 어댑터 #11

| 할 일 | 담당 | 언제까지 |
|---|---|---|
| 실제 시드: 이미지·Live2D 파일과 모션 목록, CDN 주소, 약관 문구를 정식 마이그레이션으로 (A6) | seonguk(content) + 에셋 제공자 | 시연 서버 배포 전 |
| 실제 카카오 로그인 어댑터 (`KakaoClient`, 카카오 앱 키·리다이렉트 URI, 동의 항목 '닉네임' 필수). 없으면 운영 프로필에서 앱이 뜨지 않음 | seonguk(user) | 카카오 앱 등록 후 |
| 실제 인증 메일 어댑터 (`EmailSender`, 발송 서비스 선택). 없으면 운영 프로필에서 앱이 뜨지 않음 | seonguk(user) | 발송 서비스 정한 뒤 |
| 실제 LLM 어댑터 (`LlmClient`, `capsule.llm.mode=real`) + `llm_usages` 기록 | seunghee(dialogue) | Phase B |
