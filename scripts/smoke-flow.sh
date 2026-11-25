#!/usr/bin/env bash
# 프론트 시나리오를 실제 요청으로 끝까지 돌린다 (떠 있는 서버에). 서버는 따로 띄운다: scripts/run-local.sh
#   scripts/smoke-flow.sh                       # 이 worktree 포트(run-local.sh --info)로
#   scripts/smoke-flow.sh http://localhost:8080 # 주소 지정
#   SMOKE_OUT=/tmp/smoke scripts/smoke-flow.sh  # 요청 · 응답 JSON을 남길 폴더 (기본: build/smoke)
# 흐름: 가입 → 공부 시작 → 집중(인사) → 하품 · 손 흔들기 · 고개 돌림 → 채팅(칭찬 · 단답 · 무례 · 공부 질문)
#       → 자리 비움 61초 → 복귀 → 쉬는 시간 → 채팅 → 다시 집중 → 같은 requestId 재전송 → 종료 → 리포트
# 끝에 단계별 표(대사 · 감정 · 표정 · 동작 · 호감도 · 단계 · 레벨 · 걸린 시간)를 찍고, 실패한 요청이 있으면 1로 끝난다.
# JSON은 python3로 읽는다 (jq 없이)
set -euo pipefail
export PYTHONIOENCODING=utf-8 # Windows python 기본 출력(cp949)은 '—' 같은 글자를 못 쓴다
cd "$(git rev-parse --show-toplevel)"

HOST="${1:-http://localhost:$(scripts/run-local.sh --info | sed 's/.*SERVER_PORT=//')}"
OUT="${SMOKE_OUT:-build/smoke}"
mkdir -p "$OUT"
rm -f "$OUT"/*.json "$OUT/rows.tsv"
TOKEN=""
N=0
FAILED=0

uuid() { python3 -c 'import uuid; print(uuid.uuid4())'; }
now() { python3 -c 'import datetime; print(datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="milliseconds").replace("+00:00","Z"))'; }
# 성공 응답은 {"success":true,"data":{...}} (api.md 공통 규칙). d는 data 안 (감싸지 않은 응답은 그대로)
field() { python3 -c 'import json,sys; d=json.load(open(sys.argv[1],encoding="utf-8")); d=d.get("data",d) if isinstance(d,dict) else d; print(eval(sys.argv[2]))' "$1" "$2"; }

# call <이름> <메서드> <경로> [본문] → 응답 파일 경로를 RES에, 상태 코드를 CODE에
call() {
  local name="$1" method="$2" path="$3" body="${4:-}"
  N=$((N + 1))
  RES="$OUT/$(printf '%02d' "$N")-$name.json"
  local args=(-s -o "$RES" -w '%{http_code} %{time_total}\n' -X "$method" "$HOST$path" -H 'Content-Type: application/json')
  [ -n "$TOKEN" ] && args+=(-H "Authorization: Bearer $TOKEN")
  # 본문은 파일로 보낸다: Windows curl은 인자의 한글을 UTF-8로 보내지 않는다
  if [ -n "$body" ]; then
    printf '%s' "$body" > "${RES%.json}.request.json"
    args+=(--data-binary "@${RES%.json}.request.json")
  fi
  read -r CODE SECS < <(curl "${args[@]}")
  case "$CODE" in 2*) ;; *) FAILED=1; echo "✖ $name $method $path → $CODE $(head -c 300 "$RES")" >&2 ;; esac
  MS=$(python3 -c 'import sys; print(int(float(sys.argv[1].strip()) * 1000))' "$SECS") # Windows curl은 끝에 \r
}

# 표 한 줄: 응답에서 캐릭터 반응 · 호감도를 꺼낸다 (18 · 19 · 20 · 22 응답 모양이 달라서 있는 칸만)
row() {
  python3 - "$RES" "$1" "$CODE" "$MS" >> "$OUT/rows.tsv" <<'PY'
import json, sys
path, step, code, ms = sys.argv[1:5]
try:
    d = json.load(open(path, encoding="utf-8"))
except Exception:
    d = {}
if isinstance(d, dict) and isinstance(d.get("data"), dict):
    d = d["data"]
c =(d.get("characters") or [d])[0] if isinstance(d, dict) else {}
a = c.get("affinity") or d.get("relation") or {}
name = lambda x: (x or {}).get("name") or (x or {}).get("group") or ""
text = c.get("content") or d.get("closingLine") or ""
# 대화(affinity)는 호감도, 끊기(relation)는 친밀도 (2026-11-23): 끊기 줄은 점수 칸에 인정 시간, 단계 칸에 끝난 단계
delta = a.get("delta", "")
points = a.get("points", a.get("creditedSeconds", ""))
stage = a.get("stage", a.get("stageAfter", ""))
level = a.get("level", "")
cells = [step, code, text.replace("|", "/").replace("\n", " "), c.get("emotion") or "", name(c.get("expression")),
         name(c.get("motion")), str(delta), str(points), str(stage), str(level), str(c.get("fallback", "")), ms]
print("\t".join(x or "-" for x in cells))  # 빈 칸은 '-': read -a가 이어진 탭을 하나로 합친다
PY
}

event() { # event <단계 이름> <type> [intensity]
  call "event-$2" POST "/api/connections/$SID/events" "{\"type\":\"$2\",\"occurredAt\":\"$(now)\"}"
  row "$1 (18)"
  local eid; eid=$(field "$RES" 'd["eventId"]')
  local extra=""; [ -n "${3:-}" ] && extra=",\"intensity\":$3"
  call "reaction-$2" POST "/api/connections/$SID/events/$eid/reaction" "{\"requestId\":\"$(uuid)\"$extra}"
  row "$1 (19)"
}

message() { # message <단계 이름> <내용> [requestId]
  LAST_RID="${3:-$(uuid)}"
  call "message" POST "/api/connections/$SID/messages" \
    "{\"requestId\":\"$LAST_RID\",\"characterId\":1,\"content\":\"$2\",\"inputType\":\"TEXT\",\"tts\":false}"
  row "$1 (20)"
}

echo "▶ $HOST (응답 JSON: $OUT)"
call health GET /actuator/health
call terms GET /api/terms
TERM_IDS=$(field "$RES" '[t["termVersionId"] for t in d["terms"]]')
# 새 카카오 사용자는 403 KAKAO_SIGNUP_REQUIRED + 가입 토큰 → 약관 동의로 가입 완료 (명세 6 · 49). 403은 실패로 세지 않는다
BEFORE=$FAILED
call login POST /api/auth/social/KAKAO "{\"code\":\"fake-kakao:smoke-$(uuid | cut -c1-8):민수\",\"redirectUri\":\"http://localhost:5173/oauth/kakao\",\"deviceHash\":\"smoke\"}" 2>/dev/null
[ "$CODE" = 403 ] && FAILED=$BEFORE
SIGNUP_TOKEN=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1],encoding="utf-8"))["detail"]["signupToken"])' "$RES")
call signup POST /api/auth/social/KAKAO/signup "{\"signupToken\":\"$SIGNUP_TOKEN\",\"agreedTermVersionIds\":$TERM_IDS,\"deviceHash\":\"smoke\"}"
TOKEN=$(field "$RES" 'd["accessToken"]')
call profile PATCH /api/me '{"avatarId":1,"birthDate":"2001-04-10"}'
call session POST /api/connections '{"mateId":1}'
SID=$(field "$RES" 'd["connectionId"]')
START=$(date +%s)

# 베타 이벤트 4종 (명세 18, 2026-11-23): PRESENT · AWAY · RETURN · IDLE_TALK
event "연결 시작 · 얼굴 보임" PRESENT
message "채팅 · 칭찬" "너 머리 진짜 좋다"
message "채팅 · 단답" "ㅇㅇ"
message "채팅 · 무례" "닥쳐 좀"
message "채팅 · 공부 질문" "x^2-5x+6=0 근이 뭐야? 답만"
event "자리 비움" AWAY 1
echo "… 자리 비움 61초 기다림"
sleep 61
event "복귀" RETURN
event "말 걸기 타이머" IDLE_TALK
message "같은 requestId 재전송" "x^2-5x+6=0 근이 뭐야? 답만" "$LAST_RID"

ELAPSED=$(( $(date +%s) - START - 2 ))
call end POST "/api/connections/$SID/end" "{\"connectedSeconds\":$ELAPSED,\"presentSeconds\":$(( ELAPSED - 61 )),\"cameraOnSeconds\":$ELAPSED,\"pipSeconds\":0}"
row "연결 끊기 (22)"
call report GET "/api/connections/$SID"
row "연결 기록 (23)"

echo
echo "| 단계 | HTTP | 대사 | 감정 | 표정 | 동작 | 호감도 변동 | 호감도 | 단계 | 레벨 | 대체 | ms |"
echo "|---|---|---|---|---|---|---|---|---|---|---|---|"
while IFS=$'\t' read -r -a c; do
  printf '| %s |\n' "$(IFS='|'; echo "${c[*]}" | sed 's/|/ | /g')"
done < "$OUT/rows.tsv"
[ "$FAILED" = 0 ] && echo "✔ 전체 흐름 통과" || { echo "✖ 실패한 요청이 있음 (위 로그)"; exit 1; }
