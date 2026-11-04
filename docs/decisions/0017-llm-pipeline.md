# 0017 LLM 파이프라인: 공급자 중립 게이트웨이, DB 프롬프트 버전, 실험실

- 상태: 실험 (2026-11-04, `feature/dialogue-llm-pipeline`에서만. develop에 넣기 전에 승희·성욱이 확인)
- 결정
  1. **LLM 호출의 입구는 `LlmGateway` 하나.** 서비스·대화 엔진·실험실은 용도(`LlmPurpose`: CHAT · DIRECTOR · ACTOR · JUDGE · REACTION · SUMMARY · CLOSING · EVAL_JUDGE)만 말한다. 게이트웨이가 공급자·모델·한도를 정하고, 재시도(429·5xx·연결 오류) → 대체 공급자 → 호출 기록(`llm_usages`)을 맡는다. 모델 거절(refusal)은 다른 공급자로 넘기지 않고 대체 대사로 끝낸다(안전 판단 우회 방지).
  2. **공급자 전환은 설정 한 줄** (`capsule.llm.provider`: fake · anthropic · openai). 용도에는 모델 이름 대신 등급(fast · smart · judge)을 적고, 등급 → 모델 표는 공급자마다 둔다. 키는 환경변수(`ANTHROPIC_API_KEY`, `OPENAI_API_KEY`)로만.
  3. **공급자 어댑터는 SDK 없이 Spring `RestClient`(JDK HttpClient)로 직접 HTTP.** Anthropic Messages API, OpenAI Chat Completions(같은 형식의 다른 서버도 base-url로). 일반 호출과 스트리밍(SSE) 모두. 형식은 로컬 가짜 서버 테스트(`LlmProviderTest`)로 고정한다.
  4. **프롬프트는 DB 버전(`prompt_versions`)에서 읽는다.** 코드는 키(`PromptKey`)로 찾는다. 고칠 때는 새 버전(DRAFT) → 실험실에서 고정(pin)해 시험 → ACTIVE. 이전 ACTIVE는 ARCHIVED, 되돌리기 = 예전 버전 다시 켜기. 캐릭터 전용 버전이 공통 버전보다 먼저. 호출마다 쓴 버전 id가 `llm_usages.prompt_version_ids`에 남는다.
     - 공통 프롬프트의 원본은 git의 시드(`classpath:prompts/`). 시작할 때 같은 본문의 공통 버전이 없으면 새 ACTIVE로 넣는다: 처음엔 버전 1, 시드 파일을 고쳐 배포하면 그 본문이 새 ACTIVE. 운영에는 실험실이 없으므로 운영 프롬프트는 이 길로만 바뀐다(SQL 손작업 없음). 실험실에서 좋았던 본문을 시드 파일로 옮겨 커밋하는 것이 "내보내기"다.
     - 캐릭터 전용(대화 엔진 파일 등)은 시드가 없고 실험실에서 올린다.
  5. **답장(명세 20) 파이프라인은 두 개.** SIMPLE: 명세 그대로 LLM 한 번(답장 + 감정 + 호감도 JSON). ENGINE: #11 대화 엔진(감독 → 배우 [→ 품질 모드 후보 3 + 심사]). 서비스는 `capsule.dialogue.chat-pipeline`으로, 실험실은 대화방마다 고른다. 같은 코드를 부르므로 실험실 품질 = 서비스 품질.
  6. **엔진 상태는 `dialogue_states`** (세션 × 캐릭터, jsonb + 낙관적 잠금 + 형식 버전). 턴이 성공했을 때만 저장, 공부 종료 때 삭제.
  7. **LLM은 DB 트랜잭션 밖에서.** 메시지 전송은 읽기 → LLM(5초) → 저장(한 트랜잭션).
     - 저장 트랜잭션은 호감도(`applyChat`)부터 부른다. applyChat은 relation 행을 잠그고, 공부 종료도 정산 때 같은 행을 잠근 채 커밋까지 간다. 그래서 답하는 사이 종료가 겹치면 종료가 끝난 뒤 세션을 다시 보고 409 `SESSION_ENDED`, 답장·호감도·엔진 상태·답 못 받은 사용자 말을 남기지 않는다. 잠금 순서가 종료(세션 → relation → 대화)와 같아 교착이 없다.
     - 같은 requestId가 처리 중이면 끝날 때까지 기다렸다가 같은 답장(명세 20). 사용자 말을 저장한 지 8초가 지나도 답이 없으면(먼저 온 요청이 죽음) 기다리지 않고 이어받는다.
     - 서비스 호출에는 마감(5초)이 붙는다. 마감이 지나면 게이트웨이가 새 호출(재시도·대체 공급자·엔진의 다음 단계)을 시작하지 않는다(결과가 버려질 호출에 돈을 쓰지 않음). 이미 보낸 호출은 끝까지 받아 실제 지연을 기록한다.
     - 공부 종료(22)는 session의 트랜잭션 안이라 요약과 완료 대사를 동시에 보내 5초 안에 끝낸다.
  8. **실험실 `/internal/llm-lab`** (local에서만 켬): 실험 대화방(연속 대화 + 턴마다 원문·지연·비용·사람 평가), 한 번 실행(같은 입력 N번), 프롬프트 버전 관리, 평가(골든 대본 × 반복, LLM 심사 0~10, 사람 1~5, 실행끼리 비교), 호출 집계(p95 지연, 5초 초과 수).
     - 보안: 전용 보안 체인, 기본 거부. 화면 주소만 열고, API는 토큰(`capsule.llm.lab.token`)이 비어 있으면 이 컴퓨터(loopback) 요청만, 넣었으면 `X-Lab-Token` 헤더가 같아야 한다.
- 이유
  - 공급자를 아직 못 정했다(OpenAI · Claude). 고른 뒤에도 장애 대비 대체 공급자가 있으면 좋다. 용도 → 등급 → 모델 두 단계면 공급자를 바꿔도 코드·용도 설정은 그대로다.
  - 기획(말투·길이·호감도·요약)이 미정이라 프롬프트가 계속 바뀐다. 배포 없이 바꾸고, 바꾼 것이 품질·비용에 준 영향을 버전별로 비교해야 한다(호출 기록에 버전 id).
  - "몇 번 돌려 보고 괜찮다"는 느낌으로 정하면 다음 수정 때 나빠져도 모른다. 같은 대본을 같은 잣대(고정된 심사관 + 사람 점수)로 다시 돌려 숫자로 비교한다.
  - 명세의 5초 대체 대사 때문에 지연이 품질만큼 중요하다. 호출마다 지연·첫 글자 시간을 남겨 감독 끄기·모델 낮추기·스트리밍 전환을 숫자로 판단한다(#11 "먼저 재 본다").
- 버린 대안
  - **Anthropic Java SDK (#11에서 정했던 것)**: 공급자 두 개를 쓰려면 OpenAI SDK까지 두 개가 들어온다(둘 다 Kotlin·OkHttp·Jackson 2 의존. Boot 4의 Jackson 3와 버전 충돌 위험). 우리가 쓰는 기능은 메시지 호출·캐시 표시·사용량·스트리밍뿐이라 어댑터 두 개(각 150줄 안팎)로 충분하다. 구조화 출력·도구 사용 같은 새 기능이 필요해지면 그 공급자만 SDK로 바꿔도 `LlmProvider` 뒤라 다른 코드는 그대로다.
  - **Spring AI**: #11의 이유 그대로(엔진이 자체 포트를 가짐, Boot 4.1 호환 미검증) + 프롬프트 캐시 지점·캐시 토큰이 추상화 뒤로 숨는다.
  - **프롬프트를 git 파일로만**: 바꿀 때마다 배포해야 하고, 실험 중인 버전과 서비스 버전을 동시에 둘 수 없다.
  - **LiteLLM 같은 프록시 서버**: 운영할 서버가 하나 더 생긴다. 2인 팀에 과하다.
  - **엔진 상태를 `dialogue_session` + `dialogue_turn` 두 테이블로(엔진 명세)**: 턴 지표는 `llm_usages`(trace_id로 묶음)로 충분하다(#11 추천안과 같음).
- 결과·주의
  - `llm_usages`가 넓어졌다: user_id·session_id NULL 허용(실험실), purpose 8종, 공급자·캐시 토큰·비용·지연·상태·프롬프트 버전·원문 칸. 서비스 호출의 원문은 기본으로 남기지 않는다(`log-content=false`, 개인정보).
  - 메시지 전송 요청에 `characterId`(명세 20, #35)를 받고 세션 캐릭터가 아니면 400 `CHARACTER_NOT_IN_SESSION`. 응답에 `characterId`·`emotion`.
  - 판단 항목은 기본값만 넣고 `TODO(judgment)`로 남겼다: 상태 반응을 LLM으로 할 trigger(J-04), 넘길 대화 수(J-05, 20개), 엔진 신호 → 호감도(J-06), 감정 → 모션·표정(J-07·#9), 요약 방법(J-08), 완료 대사 정보(J-09).
  - **J-04 임시 기본값은 명세 19와 다르다.** 명세는 "LLM으로 대사"인데, 기본값은 #11 추천안대로 전부 고정 대사(`reaction-llm-triggers: []`, 즉시·비용 0)다. J-04가 정해지면 trigger를 넣거나(섞기 = `[GREETING]`) 명세를 고친다.
  - **고정 대사는 호감도 단계별 묶음에서 무작위로 하나** (`fixed.lines`의 `byLevel`: 단계 N 이상 칸, 현재 단계 이하 중 가장 높은 칸, 없으면 `lines`). 반응 서비스가 relation `api`에서 단계를 읽는다. LLM 반응도 단계(`{{relationLevel}}`)와 그 단계의 묶음을 예시로 받는다. API(18 · 19)는 그대로: AWAY 2 · 3단계와 TTS 때문에 19는 어차피 남고, 고정 대사면 19는 몇 ms라 왕복 한 번 말고는 비용이 없다. 프론트가 대사를 고르는 안은 버림(대사 수정 = 프론트 배포, 캐릭터별 · 버전 · 실험실 · LLM 전환이 서버 밖으로 빠짐). 대사 문구는 임시, 기획이 채운다.
  - develop 위로 다시 올리며(#37 · #41 뒤) 지킨 것: 19 · 20 모션 · 표정은 대사 감정으로 고른다(`ReactionQuery.pick(캐릭터, 감정, null)`, 감정 null이면 동작 없음). 20은 사용자 말을 먼저 저장하고 한도를 검사한다(처리 중 재전송은 한도에 안 셈, 429면 저장한 말을 지움). 동작 이벤트 고정 대사 · 감정은 `fixed.lines`로 옮겼다(옛 `FakeLlmClient` 감정 = 칸의 `emotion`). `HEAD_TURN`(#44 · #45 · #47) 칸과 실험실 trigger 목록도 미리 넣었다.
  - LLM이 5초를 넘긴 19의 감정은 지금 null이다(동작 없음). 판단 백로그 J-12 ② 추천(상황별 감정을 그대로)으로 정하면 `DialogueService.react`의 대체 줄 하나를 바꾼다.
  - `judgment-backlog.md`의 J-04~J-09 '위치' 칸은 지운 `LlmClient`·`FakeLlmClient`를 가리킨다. develop에 넣을 때 명세 PR에서 새 위치(`ReactionWriter`·`SimpleChatPipeline`·`EngineChatPipeline`·`ClosingWriter`·`fixed.lines`)로 고친다.
  - 하네스 문서(`verify` 스킬, HARNESS.md)의 `capsule.llm.mode`는 이 브랜치가 들어갈 때 `capsule.llm.provider`로 고친다(하네스 PR).
