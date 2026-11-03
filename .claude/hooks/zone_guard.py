#!/usr/bin/env python3
"""PreToolUse(Edit|Write|MultiEdit) 훅: 파일을 고치기 직전에 구역을 판정한다.

- allow: 아무 출력 없이 통과 (평소 권한 흐름 그대로)
- ask:   사람에게 확인 요청 (사유는 사람 화면에 표시)
- deny:  차단 (사유가 Claude에게 전달돼 스스로 경로를 바꿈)
판정 규칙 자체는 .harness/zones.py 의 decide() 에 있다.
"""
import json
import os
import re
import sys


def native(p: str) -> str:
    """Windows에서 Git Bash 형식(/c/Users/...)을 C:/Users/...로. 안 하면 C:\\c\\Users로 읽혀 판정 없이 통과함."""
    return re.sub(r"^/([a-zA-Z])/", r"\1:/", p) if os.name == "nt" else p


ROOT = os.environ.get("CLAUDE_PROJECT_DIR") or os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, ".harness"))


def main() -> int:
    try:
        data = json.load(sys.stdin.buffer)  # 바이트로 읽어 UTF-8 판별 (Windows 기본 cp949로 디코딩하지 않게)
    except Exception:
        return 0
    tool_input = data.get("tool_input") or {}
    path = tool_input.get("file_path") or tool_input.get("notebook_path")
    if not path:
        return 0
    path = native(path)
    # stdin JSON의 cwd는 Git Bash가 변환해주지 않으므로 같이 바꾼다 (환경변수 ROOT는 Git Bash가 이미 변환함)
    abs_path = os.path.realpath(path if os.path.isabs(path) else os.path.join(native(data.get("cwd") or ROOT), path))
    root = os.path.realpath(ROOT)
    if not abs_path.startswith(root + os.sep):
        return 0  # 레포 밖 파일은 관여하지 않음
    rel = os.path.relpath(abs_path, root)

    try:
        import zones
        cfg = zones.load()
        decision, reason = zones.decide(rel, cfg, zones.current_member(cfg), zones.current_phase(), zones.current_branch())
    except Exception as e:  # 하네스 오류로 작업이 막히지 않게: 경고만 남기고 통과
        print(f"[하네스] zone_guard 오류(통과 처리): {e}", file=sys.stderr)
        return 0

    if decision == "allow":
        return 0
    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": decision,
            "permissionDecisionReason": reason,
        }
    }, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
