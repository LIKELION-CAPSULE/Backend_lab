#!/usr/bin/env bash
# =====================================================================
# 캡슐의 단 하나의 검증 명령.
# 사람도, Claude Code(Stop 훅)도, CI도 이것만 호출한다. 기준이 하나라서
# "내 로컬에선 됐는데"가 생기지 않는다.
#
#   scripts/verify.sh            = --full (PR 올리기 전, CI)
#   scripts/verify.sh --quick    = Stop 훅용 (포맷 자동정리 + 컴파일 + 모듈 경계 + 바뀐 모듈 테스트)
# =====================================================================
set -uo pipefail
MODE="${1:---full}"
cd "$(git rev-parse --show-toplevel)" || exit 1

step() { printf '\n▶ %s\n' "$*"; }
fail() { printf '\n✖ %s\n' "$*"; exit 1; }

step "하네스 설정 점검 (zones.conf)"
python3 .harness/zones.py check || fail "zones.conf 오류. docs/HARNESS.md '구역표' 참고"

# -x가 아니라 -f: Windows에서 실행 비트 없이 커밋된 gradlew를 "프로젝트 없음"으로 보고 조용히 통과하지 않게
if [ ! -f ./gradlew ]; then
  printf '\nℹ 아직 Gradle 프로젝트가 없음(뼈대 생성 전). 하네스 점검만 하고 통과.\n'
  exit 0
fi

BASE_PKG="$(sed -n 's/^base_package *= *//p' .harness/zones.conf | head -n1)"

case "$MODE" in
  --quick)
    step "포맷 자동 정리 (spotlessApply)"
    ./gradlew -q spotlessApply || fail "포맷 정리 실패"

    step "컴파일 (main + test)"
    ./gradlew -q testClasses || fail "컴파일 실패"

    filters=(--tests '*ModularityTests')
    for m in $(python3 .harness/zones.py changed-modules); do
      filters+=(--tests "${BASE_PKG}.${m}.*")
    done
    step "모듈 경계 + 바뀐 모듈 테스트: ${filters[*]}"
    ./gradlew -q test "${filters[@]}" || fail "테스트 실패 (모듈 경계 위반이면 ModularityTests 메시지 확인)"
    ;;
  --full)
    step "포맷 검사 (spotlessCheck)"
    ./gradlew spotlessCheck || fail "포맷 위반 → ./gradlew spotlessApply 후 다시"

    step "빌드 + 전체 테스트 (모듈 경계, 전체 플로우 E2E 포함)"
    ./gradlew build || fail "빌드/테스트 실패"
    ;;
  *)
    fail "알 수 없는 옵션: $MODE (사용: --quick | --full)"
    ;;
esac

printf '\n✔ verify 통과 (%s)\n' "$MODE"
