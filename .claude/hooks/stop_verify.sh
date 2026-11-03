#!/usr/bin/env bash
# Stop 훅: Claude가 "다 했다"고 멈추기 직전에 verify.sh --quick 을 돌린다.
# 실패하면 exit 2 → 오류 내용이 Claude에게 전달되고, Claude는 멈추지 않고 고치러 간다.
# 같은 세션에서 연속 3번 막았는데도 실패하면 더 막지 않고 사람에게 넘긴다(무한 반복 방지).
MAX_RETRY=3

input="$(cat)"
session_id="$(printf '%s' "$input" | python3 -c 'import json,sys
try: print(json.load(sys.stdin).get("session_id","default"))
except Exception: print("default")' 2>/dev/null)"
counter="${TMPDIR:-/tmp}/capsule-stop-${session_id:-default}"

cd "${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}" || exit 0

# 코드/빌드 변경이 없으면 검증할 것이 없음
if [ -z "$(git status --porcelain -- src build.gradle* settings.gradle* gradle 2>/dev/null)" ]; then
  rm -f "$counter"
  exit 0
fi

out="$(scripts/verify.sh --quick 2>&1)"
code=$?
if [ $code -eq 0 ]; then
  rm -f "$counter"
  exit 0
fi

count=$(( $(cat "$counter" 2>/dev/null || echo 0) + 1 ))
if [ "$count" -gt "$MAX_RETRY" ]; then
  rm -f "$counter"
  echo "[하네스] verify가 ${MAX_RETRY}번 연속 실패해서 더 막지 않음. 사람이 오류를 확인할 것." >&2
  exit 0
fi
echo "$count" > "$counter"
{
  echo "[하네스] scripts/verify.sh --quick 실패 (${count}/${MAX_RETRY}). 아래 오류를 고친 뒤에 끝낼 것."
  echo "(모듈 경계 위반이면 상대 모듈의 api 패키지나 이벤트를 쓰도록 바꿀 것. 자세한 해석은 verify 스킬)"
  echo "-----"
  printf '%s\n' "$out" | tail -n 60
} >&2
exit 2
