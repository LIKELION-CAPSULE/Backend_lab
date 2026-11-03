#!/usr/bin/env python3
"""PreToolUse(Bash) 훅: git·GitHub가 대신 막아주지 않는 두 가지만 막는다.
(마이그레이션 파일 삭제·이동, .env 비밀값 읽기)
"""
import json
import re
import sys

RULES = [
    # (판정, 정규식, 사유)
    # push 규칙(force push, develop/main 직접 push)은 GitHub 브랜치 보호가 막고,
    # 그 밖의 위험한 명령은 Claude Code 기본 권한 확인이 물어보므로 여기서 다시 만들지 않는다.
    ("deny", r"\b(rm|mv|git\s+rm|git\s+mv)\b[^;&|]*db[/\\]migration",
     "마이그레이션 파일을 삭제/이동하지 않음. 변경은 scripts/new-migration.sh로 새 파일을 만들 것."),
    ("deny", r"\b(cat|less|more|head|tail|bat|source|grep|sed|awk)\b[^;&|]*\.env(?!\.example)\b",
     ".env 파일은 비밀값이 있어 읽지 않음. 필요한 변수 이름은 .env.example을 볼 것."),
]


def main() -> int:
    try:
        data = json.load(sys.stdin.buffer)  # 바이트로 읽어 UTF-8 판별 (Windows 기본 cp949로 디코딩하지 않게)
    except Exception:
        return 0
    command = (data.get("tool_input") or {}).get("command", "")
    for decision, pattern, reason in RULES:
        if re.search(pattern, command):
            print(json.dumps({
                "hookSpecificOutput": {
                    "hookEventName": "PreToolUse",
                    "permissionDecision": decision,
                    "permissionDecisionReason": reason,
                }
            }))  # ASCII 이스케이프: Windows(cp949)에서도 깨지지 않음
            return 0
    return 0


if __name__ == "__main__":
    sys.exit(main())
