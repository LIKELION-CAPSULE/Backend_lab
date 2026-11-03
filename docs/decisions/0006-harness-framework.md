# 0006 하네스 프레임워크: 기성 팩 대신 Claude Code 기본 기능으로 직접 구성

- 결정: Superpowers, pstack, CCPM 같은 팩을 설치하지 않는다. Claude Code의 훅·스킬·서브에이전트·settings + GitHub Actions(anthropics/claude-code-action)로 구성한다.
- 이유: 조사한 팩 어디에도 "2인이 모듈을 나눠 갖고, 남의 모듈 수정 시 멈추는" 장치가 없어 그 부분은 직접 만들어야 했다. 나머지는 기본 기능으로 충분하고, 팩을 겹쳐 쓰면 스킬끼리 충돌한다. 의존성은 python3, git, gh뿐. git·GitHub 기능은 다시 만들지 않는다(0010).
- 가져온 아이디어: Superpowers(코드 전 계획 확인, 완료 전 검증), pstack(검증 방법을 스킬로), CCPM(백로그 → 이슈), DeepSeek Harness 글(규칙은 구조에).
- 버린 대안: Superpowers 전체(1인 기준 고정 공정, TDD 루프가 느린 통합 테스트와 안 맞음), pstack(Bun·Graphite 의존, TS 중심), CCPM 전체(공정이 무거움), 오케스트레이터(자율성 높아 통제 어려움), 충돌 예측 자동화(Clash/Grove 방식: 구역 분리·짧은 브랜치·공용 PR 우선으로 충분하고, 남는 충돌은 git이 rebase·PR에서 알려줌).
- 대가: 유지보수를 우리가 한다 → 판정 로직을 .harness/zones.py 한 파일로 모음.
