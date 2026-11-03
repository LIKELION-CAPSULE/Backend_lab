#!/usr/bin/env python3
"""SessionStart 훅: 세션 시작마다 '지금 내 상황'을 Claude 컨텍스트에 넣는다.

stdout으로 출력한 내용이 그대로 Claude에게 전달된다. 빠르게 끝나야 하므로 네트워크는 쓰지 않는다.
"""
import os
import subprocess
import sys

ROOT = os.environ.get("CLAUDE_PROJECT_DIR") or os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, ".harness"))


def run(cmd: list[str], timeout: int = 15) -> str:
    try:
        return subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, timeout=timeout).stdout.strip()
    except Exception:
        return ""


def main() -> int:
    try:
        import zones
        cfg = zones.load()
    except Exception as e:
        print(f"[캡슐 하네스] zones.conf를 읽지 못함: {e}")
        return 0

    me = zones.current_member(cfg)
    phase = zones.current_phase()
    branch = zones.current_branch()
    mine = [m for m, o in cfg.modules.items() if o == me]
    lines = ["[캡슐 하네스 상태]"]
    lines.append(f"- 나: {me or '미설정 (scripts/setup.sh 필요)'} | 내 모듈: {', '.join(mine) or '-'}")
    if phase == "skeleton":
        lines.append("- 단계: skeleton (얇은 뼈대 만드는 중. 구역 제한 완화, skeleton 스킬 절차를 따를 것)")
    else:
        lines.append("- 단계: domain (도메인 분리 개발. 내 모듈 밖 수정은 사람 확인을 거침)")
    lines.append(f"- 브랜치: {branch}")
    if branch in zones.PROTECTED_BRANCHES:
        lines.append(f"- ⚠ {branch}에서는 코드를 고치지 않는다. 작업 전에 feature/<모듈>-<설명> 브랜치를 만들 것.")

    source = os.path.join(ROOT, "docs", "specs", "SOURCE.md")
    if os.path.exists(source):
        with open(source, encoding="utf-8") as f:
            line = next((l.split(":", 1)[1].strip() for l in f if l.startswith("노션 사본 반영")), "")
            synced = line.split(". ")[0]  # 첫 문장(날짜·커밋·차수)만
        if synced:
            lines.append(f"- 명세: 원본은 docs/specs (고칠 때는 명세 PR 먼저) | 노션 사본 반영: {synced} (명세 PR 머지 뒤 spec-sync)")

    backlog = os.path.join(ROOT, "docs", "specs", "judgment-backlog.md")
    if phase == "domain" and os.path.exists(backlog):
        lines.append("- 미구현 판단 목록: docs/specs/judgment-backlog.md (내 담당 항목부터)")
    lines.append("- 규칙: 검증은 scripts/verify.sh 하나 | 계약 변경은 contract-change 스킬 | PR은 pr-write 스킬 | 상세는 docs/HARNESS.md")
    print("\n".join(lines))
    return 0


if __name__ == "__main__":
    sys.exit(main())
