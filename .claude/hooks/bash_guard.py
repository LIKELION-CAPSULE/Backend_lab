#!/usr/bin/env python3
"""PreToolUse(Bash) 훅: git·GitHub가 대신 막아주지 않는 것만 막는다.
(마이그레이션 파일 삭제·이동, .env 비밀값 읽기, develop/main으로 push)
"""
import json
import re
import subprocess
import sys

RULES = [
    # (판정, 정규식, 사유)
    # 그 밖의 위험한 명령은 Claude Code 기본 권한 확인이 물어보므로 여기서 다시 만들지 않는다.
    ("deny", r"\b(rm|mv|git\s+rm|git\s+mv)\b[^;&|]*db[/\\]migration",
     "마이그레이션 파일을 삭제/이동하지 않음. 변경은 scripts/new-migration.sh로 새 파일을 만들 것."),
    ("deny", r"\b(cat|less|more|head|tail|bat|source|grep|sed|awk)\b[^;&|]*\.env(?!\.example)\b",
     ".env 파일은 비밀값이 있어 읽지 않음. 필요한 변수 이름은 .env.example을 볼 것."),
]

# develop/main push는 원래 GitHub 브랜치 보호의 일. 조직 Free 플랜이라 그걸 못 쓰는 동안만 여기서 막는다(ADR 0010 예외).
# 브랜치 보호를 켜면 이 부분을 지운다.
PUSH_RE = re.compile(r"\bgit\s+push\b([^;&|]*)")
PROTECTED_RE = re.compile(r"[\s:](?:refs/heads/)?(develop|main)(?=$|\s)")  # origin develop, HEAD:main (feature/main-x는 아님)
PUSH_REASON = ("develop/main에는 PR로만 반영한다(GitHub 브랜치 보호가 없어 하네스가 대신 막음). "
               "작업 브랜치를 push하고 pr-write 스킬로 PR을 올릴 것.")


def current_branch(cwd):
    try:
        return subprocess.run(["git", "branch", "--show-current"], cwd=cwd or None,
                              capture_output=True, text=True, timeout=10).stdout.strip()
    except Exception:
        return ""


def pushes_protected(command, cwd):
    for m in PUSH_RE.finditer(command):
        if PROTECTED_RE.search(" " + m.group(1)) or current_branch(cwd) in ("develop", "main"):
            return True
    return False


def deny(reason):
    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": reason,
        }
    }))  # ASCII 이스케이프: Windows(cp949)에서도 깨지지 않음


def main() -> int:
    try:
        data = json.load(sys.stdin.buffer)  # 바이트로 읽어 UTF-8 판별 (Windows 기본 cp949로 디코딩하지 않게)
    except Exception:
        return 0
    command = (data.get("tool_input") or {}).get("command", "")
    for _, pattern, reason in RULES:
        if re.search(pattern, command):
            deny(reason)
            return 0
    if pushes_protected(command, data.get("cwd")):
        deny(PUSH_REASON)
    return 0


if __name__ == "__main__":
    sys.exit(main())
