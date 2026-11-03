#!/usr/bin/env bash
# 개발자 1명당 1번 (클론 직후, 새 worktree마다는 필요 없음)
# git hook은 쓰지 않음: develop/main 보호는 GitHub 브랜치 보호, 로컬 파일은 .gitignore가 맡음
#   scripts/setup.sh            # 대화형
#   scripts/setup.sh --me seonguk
set -uo pipefail
cd "$(git rev-parse --show-toplevel)" || exit 1

ok()   { printf '  ✔ %s\n' "$*"; }
warn() { printf '  ⚠ %s\n' "$*"; }

echo "▶ 필수 도구 확인"
# Windows: python3가 Microsoft Store 바로가기(실행하면 exit 49)면 훅이 전부 꺼진다(command -v로는 못 걸러냄).
# 실제 python으로 연결하는 ~/bin/python3를 만든다. 관리자 권한 불필요, Git Bash와 Claude Code 훅은 PATH 맨 앞에 ~/bin을 둠
if ! python3 -c 'import sys' 2>/dev/null; then
  real="$(command -v python || true)"
  if [ -n "$real" ] && "$real" -c 'import sys' 2>/dev/null; then
    mkdir -p "$HOME/bin"
    printf '#!/usr/bin/env bash\nexec "%s" "$@"\n' "$real" > "$HOME/bin/python3"
    chmod +x "$HOME/bin/python3"
    export PATH="$HOME/bin:$PATH"; hash -r
    ok "python3 연결 생성: ~/bin/python3 → $real"
  fi
fi
python3 -c 'import sys' 2>/dev/null && ok "python3 $(python3 -c 'import sys;print(sys.version.split()[0])')" \
  || warn "python3가 실행되지 않음 (훅이 전부 꺼짐). Python 3 설치 후 다시 실행 (docs/HARNESS.md 11장)"
gv="$(git --version | awk '{print $3}')"
ok "git $gv"
command -v docker >/dev/null && ok "docker" || warn "docker 없음 (Testcontainers, 로컬 DB에 필요)"
command -v java >/dev/null && ok "java $(java -version 2>&1 | head -n1)" || warn "java 없음 (21 권장)"
command -v gh >/dev/null && ok "gh (GitHub CLI)" || warn "gh 없음 (pr-write, review-pr 스킬에 필요)"

chmod +x scripts/*.sh .claude/hooks/*.sh .claude/hooks/*.py .harness/zones.py 2>/dev/null
if command -v claude >/dev/null && claude mcp list 2>/dev/null | grep -qi notion; then
  ok "Claude Code Notion MCP 연결됨 (spec-sync)"
else
  warn "spec-sync를 쓰려면: claude mcp add --transport http notion https://mcp.notion.com/mcp → Claude Code에서 /mcp 로그인"
fi

ME=""
if [ "${1:-}" = "--me" ]; then ME="${2:-}"; fi
if [ -z "$ME" ]; then
  keys="$(sed -n '/^\[team\]/,/^\[/p' .harness/zones.conf | grep -Ev '^\[|^#|^$' | awk '{print $1}' | paste -sd '/' -)"
  read -r -p "▶ 너는 누구? ($keys): " ME
fi

echo "▶ 내 정보를 .claude/settings.local.json 에 저장 (커밋 안 됨)"
python3 - "$ME" <<'PY'
import json, os, sys
me = sys.argv[1]
path = ".claude/settings.local.json"
data = {}
if os.path.exists(path):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
data.setdefault("env", {})["HARNESS_ME"] = me
with open(path, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
    f.write("\n")
PY
ok "HARNESS_ME=$ME"

if [ ! -f .env.local ] && [ -f .env.example ]; then
  cp .env.example .env.local && ok ".env.local 생성 (.env.example 복사, 값은 직접 채우기)"
fi

echo "▶ 결과"
HARNESS_ME="$ME" python3 .harness/zones.py whoami
python3 .harness/zones.py check --strict || warn "zones.conf의 TODO(GitHub 아이디/이메일)를 채워야 CI가 통과함"
