# 판단 백로그

뼈대 단계에서 "자리만 만든" 판단 영역의 목록이다. 지금은 결정적인 Fake가 돌고, 코드에는 `// TODO(judgment): J-xx` 주석이 있다.
도메인 단계에서 담당자가 자기 항목을 가져가 기획과 함께 정한다. 구현하는 PR은 본문 '목적'에 ID를 적고, 머지되면 이 표의 상태를 '완료'로 바꾼다.

| ID | 모듈 | 담당 | 무엇이 미정인가 | 코드 위치 | 지금 Fake 동작 | 근거 명세 | 상태 |
|---|---|---|---|---|---|---|---|
| J-01 | relation | seunghee | 상태 이벤트별 호감도 변화량 | (합친 뒤 채움) | 명세 예시값: 처음 만난 날 첫 FOCUS +5(FIRST_MEET), AWAY −1, RETURN +1, BREAK 0 | api.md 18 "예시, 기획 확정 필요" | 미정 |
| J-02 | relation | seunghee | 공부 시간 → STUDY_TIME 점수 공식 | (합친 뒤 채움) | 공부 1분당 +1 | api.md 22 | 미정 |
| J-03 | relation | seunghee | 레벨 기준 포인트·레벨 이름 | (합친 뒤 채움) | Lv.1 0점 '아는 사이', Lv.2 100점 '친한 사이', Lv.3 300점 'Lv.3' | api.md 12, 22 | 미정 |
| J-04 | dialogue | seunghee | 이벤트 반응 대사(LLM)와 trigger별 고정 대사(5초 초과) | (합친 뒤 채움) | trigger별 고정 문장 1개 | api.md 19 | 미정 |
| J-05 | dialogue | seunghee | 대화 답장 생성 (캐릭터 설정·요약·대화·일정, 답 길이) | (합친 뒤 채움) | 고정 문장 + 입력 일부 | api.md 20 | 미정 |
| J-06 | dialogue | seunghee | 대화 호감도 판정 (−5~5) | (합친 뒤 채움) | 항상 +1 | api.md 20 | 미정 |
| J-07 | dialogue | seunghee | 답장 감정 → 모션·표정 고르는 규칙 | (합친 뒤 채움) | 모션·표정 없음 | api.md 20 | 미정 |
| J-08 | dialogue | seunghee | 세션 종료 시 대화 요약 합치기 | (합친 뒤 채움) | 기존 요약 + "메시지 n개" 한 줄 | api.md 22 | 미정 |
| J-09 | dialogue | seunghee | 완료 대사 `closingLine` 생성 | (합친 뒤 채움) | 캐릭터 이름을 넣은 고정 문구 | api.md 22·23 | 미정 |
| J-10 | dialogue | seunghee | TTS `audioBase64` | (합친 뒤 채움) | 항상 null | api.md 19·20 | 미정 |
| J-11 | session | seonguk | 종료 시 프론트 값과 서버 계산값이 "크게" 다를 때의 기준 | (합친 뒤 채움) | 명세의 부등식 검증만 하고 프론트 값 사용 | api.md 22 "같이 챙길 것" | 미정 |

## 판단은 아니지만 남은 일
| 할 일 | 담당 | 언제까지 |
|---|---|---|
| 실제 시드: 이미지·Live2D 파일과 모션 목록, CDN 주소, 약관 문구를 정식 마이그레이션으로 (A6) | seonguk(content) + 에셋 제공자 | 시연 서버 배포 전 |
| 실제 카카오 로그인 어댑터 (`KakaoClient`, 카카오 앱 키·리다이렉트 URI, 동의 항목 '닉네임' 필수) | seonguk(user) | 카카오 앱 등록 후 |
| 실제 인증 메일 어댑터 (`EmailSender`, 발송 서비스 선택) | seonguk(user) | 발송 서비스 정한 뒤 |
| 실제 LLM 어댑터 (`LlmClient`, `capsule.llm.mode=real`) + `llm_usages` 기록 | seunghee(dialogue) | Phase B |
