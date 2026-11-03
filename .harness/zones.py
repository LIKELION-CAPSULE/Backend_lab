#!/usr/bin/env python3
"""캡슐 하네스 핵심 모듈.

zones.conf 를 읽어서 "이 파일은 어느 구역이고, 지금 이 사람이 고쳐도 되는가"를 판정한다.
Claude Code 훅과 CI(PR 검사·리뷰 게이트)가 전부 이 파일 하나를 쓴다.
→ 규칙이 한 곳에만 있으므로 로컬과 CI 판정이 어긋나지 않는다.

사람용 명령:
  python3 .harness/zones.py whoami                 # 나는 누구, 내 모듈
  python3 .harness/zones.py explain <경로>          # 이 파일의 구역/담당/판정
  python3 .harness/zones.py check [--strict]       # zones.conf 검사 (--strict: TODO 남으면 실패)
  python3 .harness/zones.py new-migration <모듈> <설명>
  python3 .harness/zones.py spec-impact            # api.md에서 바뀐 API → 컨트롤러·모듈·담당
CI/훅용 명령: classify, other-reviewer, check-pr-body [--spec-changed], check-pr-title, frozen-migrations, changed-modules, adr-hint
"""
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from dataclasses import dataclass, field
from datetime import datetime, timedelta

# Windows 기본 인코딩(cp949) 대신 UTF-8로: ✔ 출력으로 죽거나, stdin의 한글(PR 본문 등)이 깨지지 않게. 훅 출력도 Claude Code가 UTF-8로 읽음
for _stream in (sys.stdin, sys.stdout, sys.stderr):
    _stream.reconfigure(encoding="utf-8")

HARNESS_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(HARNESS_DIR)
CONF_PATH = os.path.join(HARNESS_DIR, "zones.conf")
PHASE_PATH = os.path.join(HARNESS_DIR, "phase")

MIGRATION_DIR = "src/main/resources/db/migration"
MIGRATION_RE = re.compile(r"^V(\d{8})_(\d{6})__([a-z0-9]+)_[a-z0-9_]+\.sql$")
PROTECTED_BRANCHES = ("develop", "main")
ZONES = ("local", "harness", "migration", "contract", "module", "shared")
# PR 본문 양식은 .github/PULL_REQUEST_TEMPLATE.md (2026-11-04 성욱 템플릿). CI는 '작업 내용'만 필수로 본다
PR_TEMPLATE_PATH = os.path.join(REPO_ROOT, ".github", "PULL_REQUEST_TEMPLATE.md")
PR_REQUIRED_SECTIONS = ["작업 내용"]


# ---------------------------------------------------------------- 설정 읽기
@dataclass
class Member:
    key: str
    github: str
    email: str


@dataclass
class Rule:
    pattern: str
    zone: str
    module: str | None
    regex: re.Pattern


@dataclass
class Config:
    base_package: str = "com.capsule"
    members: dict[str, Member] = field(default_factory=dict)
    modules: dict[str, str] = field(default_factory=dict)  # 모듈 → 담당자 키
    rules: list[Rule] = field(default_factory=list)


def glob_to_regex(pattern: str) -> re.Pattern:
    """gitignore 비슷한 글롭 → 정규식. ** 는 디렉터리 여러 단계, * 는 한 단계 안."""
    out, i = "", 0
    while i < len(pattern):
        if pattern.startswith("**/", i):
            out += "(?:.*/)?"
            i += 3
        elif pattern.startswith("**", i):
            out += ".*"
            i += 2
        elif pattern[i] == "*":
            out += "[^/]*"
            i += 1
        elif pattern[i] == "?":
            out += "[^/]"
            i += 1
        else:
            out += re.escape(pattern[i])
            i += 1
    return re.compile("^" + out + "$")


def load(conf_path: str = CONF_PATH) -> Config:
    cfg = Config()
    section = None
    raw_rules: list[tuple[str, str]] = []
    with open(conf_path, encoding="utf-8") as f:
        for lineno, raw in enumerate(f, 1):
            line = raw.split(" #")[0].split("\t#")[0].strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("[") and line.endswith("]"):
                section = line[1:-1].strip()
                continue
            parts = line.split()
            if section == "project":
                key, _, value = line.partition("=")
                if key.strip() == "base_package":
                    cfg.base_package = value.strip()
            elif section == "team":
                if len(parts) < 3:
                    raise ValueError(f"zones.conf:{lineno} [team] 줄은 '키 GitHub아이디 이메일' 형식")
                cfg.members[parts[0]] = Member(parts[0], parts[1].lstrip("@"), parts[2])
            elif section == "modules":
                if len(parts) < 2:
                    raise ValueError(f"zones.conf:{lineno} [modules] 줄은 '모듈 담당자' 형식")
                cfg.modules[parts[0]] = parts[1]
            elif section == "zones":
                if len(parts) != 2 or parts[1] not in ZONES:
                    raise ValueError(f"zones.conf:{lineno} [zones] 줄은 '패턴 구역' 형식, 구역은 {ZONES}")
                raw_rules.append((parts[0], parts[1]))
    base_path = cfg.base_package.replace(".", "/")
    for pattern, zone in raw_rules:
        pattern = pattern.replace("{base}", base_path)
        if "{module}" in pattern:
            for module in cfg.modules:
                p = pattern.replace("{module}", module)
                cfg.rules.append(Rule(p, zone, module, glob_to_regex(p)))
        else:
            cfg.rules.append(Rule(pattern, zone, None, glob_to_regex(pattern)))
    return cfg


def validate(cfg: Config, strict: bool = False) -> list[str]:
    errors = []
    if not cfg.members:
        errors.append("[team]이 비어 있음")
    for module, owner in cfg.modules.items():
        if owner not in cfg.members:
            errors.append(f"모듈 '{module}'의 담당 '{owner}'가 [team]에 없음")
    if not any(r.zone == "module" for r in cfg.rules):
        errors.append("[zones]에 module 구역 규칙이 없음")
    if strict:
        for m in cfg.members.values():
            if m.github.startswith("TODO") or m.email.startswith("TODO"):
                errors.append(f"[team] '{m.key}'의 GitHub 아이디/이메일이 TODO 상태")
    return errors


# ---------------------------------------------------------------- 환경 정보
def git(*args: str, cwd: str = REPO_ROOT) -> tuple[int, str]:
    try:
        # Windows 기본(cp949) 대신 UTF-8로 읽음: 한글이 든 diff(api.md 등)에서 깨지지 않게
        p = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True,
                           encoding="utf-8", errors="replace", timeout=20)
        return p.returncode, p.stdout.strip()
    except Exception:
        return 1, ""


def current_member(cfg: Config) -> str | None:
    me = os.environ.get("HARNESS_ME", "").strip()
    if me in cfg.members:
        return me
    _, email = git("config", "user.email")
    for m in cfg.members.values():
        if email and m.email.lower() == email.lower():
            return m.key
    return None


def member_by_github(cfg: Config, login: str) -> str | None:
    for m in cfg.members.values():
        if m.github.lower() == login.lower():
            return m.key
    return None


def current_phase() -> str:
    try:
        with open(PHASE_PATH, encoding="utf-8") as f:
            value = f.read().strip()
        return value if value in ("skeleton", "domain") else "domain"
    except FileNotFoundError:
        return "domain"


def current_branch() -> str:
    _, branch = git("rev-parse", "--abbrev-ref", "HEAD")
    return branch or ""


def is_frozen_migration(relpath: str, base: str = "origin/develop") -> bool:
    """develop에 이미 들어간 마이그레이션 = 누군가의 DB에 적용됐을 수 있음 → 불변."""
    code, _ = git("cat-file", "-e", f"{base}:{relpath}")
    return code == 0


# ---------------------------------------------------------------- 판정
def classify(relpath: str, cfg: Config) -> tuple[str, str | None]:
    relpath = relpath.replace("\\", "/")  # Windows 경로(src\main\...)도 같은 패턴으로 판정
    if relpath.startswith("./"):
        relpath = relpath[2:]
    for rule in cfg.rules:
        if rule.regex.match(relpath):
            return rule.zone, rule.module
    return "shared", None


def owner_of(cfg: Config, zone: str, module: str | None, relpath: str) -> str | None:
    if zone in ("module", "contract") and module:
        return cfg.modules.get(module)
    if zone == "migration":
        m = MIGRATION_RE.match(os.path.basename(relpath))
        if m:
            return cfg.modules.get(m.group(3))
    return None


def decide(relpath: str, cfg: Config, me: str | None, phase: str, branch: str,
           frozen: bool | None = None) -> tuple[str, str]:
    """반환: (allow|ask|deny, 사유). deny 사유는 Claude에게 보이고, ask 사유는 사람에게 보인다."""
    relpath = relpath.replace("\\", "/")  # 아래 git cat-file도 / 경로만 알아들음
    zone, module = classify(relpath, cfg)

    if zone == "local":
        return "allow", "로컬 전용 파일"

    if branch in PROTECTED_BRANCHES:
        return "deny", (f"{branch} 브랜치에서는 직접 수정하지 않는다. "
                        f"먼저 `git switch -c feature/<모듈>-<설명>`으로 브랜치를 만들고 진행할 것.")

    if zone == "migration":
        if frozen is None:
            frozen = is_frozen_migration(relpath)
        if frozen:
            return "deny", ("develop에 이미 들어간 마이그레이션은 수정 금지(Flyway 체크섬이 깨짐). "
                            "변경은 새 파일로: `scripts/new-migration.sh <모듈> <설명>`")
        m = MIGRATION_RE.match(os.path.basename(relpath))
        if not m:
            return "deny", ("마이그레이션 파일명 규칙 위반. 직접 만들지 말고 "
                            "`scripts/new-migration.sh <모듈> <설명>`으로 생성할 것.")
        if phase == "skeleton":
            return "allow", "뼈대 단계"
        mod = m.group(3)
        owner = cfg.modules.get(mod)
        if owner is None:
            return "ask", f"[하네스] 마이그레이션의 모듈 '{mod}'이 zones.conf에 없음."
        if owner == me:
            return "allow", "내 모듈 마이그레이션"
        return "ask", f"[하네스] {mod} 모듈({owner} 담당)의 테이블을 바꾸는 마이그레이션. 승인하면 PR에서 Claude 자동 리뷰가 남의 모듈 변경으로 봄. {owner}에게 한 줄 공유."

    if zone == "harness":
        if branch.startswith("harness/"):
            return "allow", "하네스 브랜치"
        return "ask", "[하네스] 하네스 파일. 규칙상 harness/* 브랜치에서 PR로 바꿈. 정말 여기서 고칠지 확인."

    if phase == "skeleton":
        return "allow", "뼈대 단계(구역 제한 완화)"

    if me is None:
        return "ask", "[하네스] 누구인지 모름. scripts/setup.sh 를 먼저 실행할 것."

    if zone == "module":
        owner = cfg.modules.get(module or "")
        if owner == me:
            return "allow", "내 모듈"
        return "ask", (f"[하네스] {module} 모듈은 {owner} 담당. 승인하면 PR에서 Claude 자동 리뷰가 남의 모듈 변경으로 봄. "
                       f"고친 뒤 {owner}에게 한 줄 공유.")

    if zone == "contract":
        owner = cfg.modules.get(module or "")
        who = "내 모듈" if owner == me else f"{owner} 담당 모듈"
        return "ask", (f"[하네스] {module} 계약(api) 변경({who}). 상대 코드가 깨질 수 있음. "
                       f"contract-change 스킬 절차(확장→이전→제거)를 따르는지 확인.")

    # 공용 구역(문서, common, gradle, yml)은 묻지 않는다. PR에서 Claude 자동 리뷰가 공용 변경으로 보므로
    # 여기서도 물으면 확인만 반복된다(ADR 0010). 묻는 건 상대 코드가 깨질 수 있는 경우(남의 모듈·계약·남의 마이그레이션)만
    return "allow", "공용 구역 (PR에서 Claude 자동 리뷰가 봄. 기능 PR에 섞지 말고 작은 공용 PR로)"


# ---------------------------------------------------------------- CI/훅 보조
def classify_labels(paths: list[str], cfg: Config, author_key: str | None) -> list[str]:
    labels: set[str] = set()
    for p in paths:
        zone, module = classify(p, cfg)
        if zone in ("contract", "shared", "migration", "harness"):
            labels.add(f"zone:{zone}")
        if module:
            labels.add(f"module:{module}")
        owner = owner_of(cfg, zone, module, p)
        if zone == "migration":
            m = MIGRATION_RE.match(os.path.basename(p))
            if m:
                labels.add(f"module:{m.group(3)}")
        if author_key and owner and owner != author_key:
            labels.add("cross-module")
    return sorted(labels)


def check_pr_body(body: str) -> list[str]:
    body = re.sub(r"<!--.*?-->", "", body or "", flags=re.S)
    # 템플릿의 안내 문구(> 로 시작하는 줄)를 그대로 두면 내용이 없는 것으로 본다
    guides = set()
    if os.path.exists(PR_TEMPLATE_PATH):
        with open(PR_TEMPLATE_PATH, encoding="utf-8") as f:
            guides = {l.strip() for l in f if l.strip().startswith(">")}
    problems = []
    for title in PR_REQUIRED_SECTIONS:
        # 제목 앞 이모지(📝 등)는 있어도 없어도 됨. ### 하위 제목(스크린샷)은 같은 섹션으로 친다
        m = re.search(rf"^##[^\S\n]*[^\w\n]*{re.escape(title)}[^\n]*$(.*?)(?=^##[^#]|\Z)", body, flags=re.M | re.S)
        if not m:
            problems.append(f"'## {title}' 섹션이 없음 (.github/PULL_REQUEST_TEMPLATE.md 양식)")
            continue
        content = [l for l in m.group(1).splitlines()
                   if l.strip() and l.strip() not in guides and not l.strip().startswith("#")]
        if not content:
            problems.append(f"'## {title}' 섹션이 비어 있음 (템플릿 안내 문구만 있음)")
    return problems


# PR 제목 = squash 머지 커밋 메시지라 커밋 규칙(<type>(<범위>): 요약)과 같게 (ADR 0014)
PR_TITLE_TYPES = ("feat", "fix", "docs", "chore", "test", "refactor", "ci")
# 범위는 여러 모듈이면 쉼표로 (refactor(dialogue,relation): ...). GitHub Revert 버튼이 만드는 'Revert "..."'도 허용
PR_TITLE_RE = re.compile(rf"^({'|'.join(PR_TITLE_TYPES)})(\([a-z0-9,-]+\))?: \S.*$")


def check_pr_title(title: str) -> list[str]:
    title = (title or "").strip()
    if PR_TITLE_RE.match(title) or title.startswith('Revert "'):
        return []
    return [f"제목 '{title}'이 커밋 규칙과 다름. `<type>(<범위>): 요약` 모양으로 "
            f"(type: {' · '.join(PR_TITLE_TYPES)}, 범위는 모듈·specs·harness 등 소문자. 예: feat(session): 공부 시작 API)"]


# ---------------------------------------------------------------- 명세 → 코드 (ADR 0015)
SPEC_API_PATH = "docs/specs/api.md"
SPEC_SECTION_RE = re.compile(r"^## (\d+)\. (GET|POST|PUT|PATCH|DELETE) (\S+)")
MAPPING_RE = re.compile(r'@(Get|Post|Put|Patch|Delete|Request)Mapping(?:\(\s*(?:(?:value|path)\s*=\s*)?"([^"]*)")?')
CODE_SYNC_RE = re.compile(r"^###[^\S\n]*코드 반영[^\n]*$(.*?)(?=^##|\Z)", re.M | re.S)


def _norm_path(path: str) -> str:
    return re.sub(r"\{[^}]*\}", "{}", path.rstrip("/") or "/")


def changed_spec_sections(base: str) -> list[str]:
    """api.md에서 base 이후 바뀐 줄이 속한 섹션 머리('## 20. POST ...' 또는 '## 공통 규칙 ...')."""
    _, diff = git("diff", "-U0", f"{base}...HEAD", "--", SPEC_API_PATH)
    lines = []
    for m in re.finditer(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@", diff, flags=re.M):
        start, count = int(m.group(1)), int(m.group(2) or 1)
        lines.extend(range(start, start + max(count, 1)))  # 지운 줄만 있으면 그 위치
    path = os.path.join(REPO_ROOT, SPEC_API_PATH)
    if not lines or not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        text = f.read().splitlines()
    headers, current = [], None
    for i, line in enumerate(text, start=1):
        if line.startswith("## "):
            current = line[3:].strip()
        if i in lines and current and current not in headers:
            headers.append(current)
    return headers


def controller_endpoints() -> dict[tuple[str, str], str]:
    """(메서드, 정규화한 주소) → '파일:줄'. 클래스 @RequestMapping과 메서드 @XxxMapping을 합친다."""
    result: dict[tuple[str, str], str] = {}
    src = os.path.join(REPO_ROOT, "src", "main", "java")
    for root, _, files in os.walk(src):
        for name in files:
            if not name.endswith("Controller.java"):
                continue
            full = os.path.join(root, name)
            rel = os.path.relpath(full, REPO_ROOT).replace(os.sep, "/")
            with open(full, encoding="utf-8") as f:
                lines = f.read().splitlines()
            prefix, seen_class = "", False
            for no, line in enumerate(lines, start=1):
                if re.search(r"\bclass\b", line):
                    seen_class = True
                m = MAPPING_RE.search(line)
                if not m:
                    continue
                kind, value = m.group(1), m.group(2) or ""
                if kind == "Request":
                    if not seen_class:
                        prefix = value
                    continue
                result[(kind.upper(), _norm_path(prefix + value))] = f"{rel}:{no}"
    return result


def spec_impact(cfg: Config, base: str) -> list[str]:
    endpoints = controller_endpoints()
    out = []
    for header in changed_spec_sections(base):
        m = SPEC_SECTION_RE.match("## " + header)
        if not m:
            out.append(f"{header} → 공통 규칙·기타 섹션. 영향 범위는 사람이 확인")
            continue
        where = endpoints.get((m.group(2), _norm_path(m.group(3))))
        if not where:
            out.append(f"{header} → 구현 없음 (새 API면 담당 모듈에서 새로 만듦)")
            continue
        zone, module = classify(where.split(":")[0], cfg)
        owner = cfg.modules.get(module or "") or "담당 없음"
        out.append(f"{header} → {where} (모듈 {module}, 담당 {owner})")
    return out


def check_code_sync(body: str) -> list[str]:
    """api.md를 바꾼 PR이면 본문에 '### 코드 반영'이 있어야 한다: 기능 PR·이슈 번호, 브랜치, 또는 '없음'."""
    body = re.sub(r"<!--.*?-->", "", body or "", flags=re.S)
    m = CODE_SYNC_RE.search(body)
    if not m:
        return ["api.md를 바꿨는데 '### 코드 반영'이 없음. `python3 .harness/zones.py spec-impact`로 영향받는 API를 보고, "
                "코드를 고칠 기능 PR·이슈 번호(#12), 브랜치(feature/...), 또는 '없음'과 이유를 적을 것"]
    content = m.group(1)
    if not re.search(r"#\d+|feature/|없음", content):
        return ["'### 코드 반영'에 기능 PR·이슈 번호(#12), 브랜치(feature/...), '없음' 중 하나가 없음"]
    return []


def working_changed_paths() -> list[str]:
    paths: set[str] = set()
    for args in (("diff", "--name-only", "HEAD"), ("ls-files", "--others", "--exclude-standard")):
        _, out = git(*args)
        paths.update(x for x in out.splitlines() if x)
    code, out = git("diff", "--name-only", "origin/develop...HEAD")
    if code == 0:
        paths.update(x for x in out.splitlines() if x)
    return sorted(paths)


def new_migration(cfg: Config, module: str, desc: str) -> str:
    if module not in cfg.modules:
        raise SystemExit(f"모듈 '{module}'이 zones.conf [modules]에 없음. 가능한 값: {', '.join(cfg.modules)}")
    desc = re.sub(r"[^a-z0-9_]+", "_", desc.lower()).strip("_")
    if not desc:
        raise SystemExit("설명은 영문 소문자/숫자/밑줄로 (예: add_affinity_table)")
    directory = os.path.join(REPO_ROOT, MIGRATION_DIR)
    os.makedirs(directory, exist_ok=True)
    latest = None
    for name in os.listdir(directory):
        m = MIGRATION_RE.match(name)
        if m:
            ts = datetime.strptime(m.group(1) + m.group(2), "%Y%m%d%H%M%S")
            latest = ts if latest is None or ts > latest else latest
    ts = datetime.now().replace(microsecond=0)
    if latest and ts <= latest:
        ts = latest + timedelta(seconds=1)
    name = f"V{ts:%Y%m%d}_{ts:%H%M%S}__{module}_{desc}.sql"
    path = os.path.join(directory, name)
    with open(path, "w", encoding="utf-8") as f:
        f.write(f"-- 모듈: {module} (담당: {cfg.modules[module]})\n"
                f"-- 이 파일이 develop에 머지되면 수정 금지. 변경은 새 마이그레이션으로.\n"
                f"-- 컬럼 변경은 확장(추가) → 이전 → 제거 순서로 나눌 것.\n\n")
    return os.path.relpath(path, REPO_ROOT)


# ---------------------------------------------------------------- CLI
def main() -> int:
    ap = argparse.ArgumentParser(description="캡슐 하네스 구역 도구")
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("check"); p.add_argument("--strict", action="store_true")
    sub.add_parser("whoami")
    p = sub.add_parser("explain"); p.add_argument("paths", nargs="+")
    p = sub.add_parser("classify"); p.add_argument("--author", default=""); p.add_argument("paths", nargs="*")
    p = sub.add_parser("other-reviewer"); p.add_argument("--author", required=True)
    p = sub.add_parser("check-pr-body"); p.add_argument("--spec-changed", action="store_true")
    sub.add_parser("check-pr-title")
    p = sub.add_parser("spec-impact"); p.add_argument("--base", default="origin/develop")
    p = sub.add_parser("frozen-migrations"); p.add_argument("--base", default="origin/develop")
    sub.add_parser("changed-modules")
    sub.add_parser("adr-hint")
    p = sub.add_parser("new-migration"); p.add_argument("module"); p.add_argument("desc")
    args = ap.parse_args()

    try:
        cfg = load()
    except Exception as e:  # 설정 오류는 모든 명령에서 바로 드러나게
        print(f"✖ zones.conf 읽기 실패: {e}", file=sys.stderr)
        return 1

    if args.cmd == "check":
        errors = validate(cfg, strict=args.strict)
        for e in errors:
            print(f"✖ {e}", file=sys.stderr)
        if not errors:
            print(f"✔ zones.conf 정상 (모듈 {len(cfg.modules)}개, 규칙 {len(cfg.rules)}개, 단계: {current_phase()})")
        return 1 if errors else 0

    if args.cmd == "whoami":
        me = current_member(cfg)
        if not me:
            print("✖ 누구인지 모름. scripts/setup.sh 실행 또는 HARNESS_ME 설정 필요")
            return 1
        mine = [m for m, o in cfg.modules.items() if o == me]
        print(f"나: {me} | 내 모듈: {', '.join(mine) or '없음'} | 단계: {current_phase()} | 브랜치: {current_branch()}")
        return 0

    if args.cmd == "explain":
        me, phase, branch = current_member(cfg), current_phase(), current_branch()
        for path in args.paths:
            rel = os.path.relpath(os.path.abspath(path), REPO_ROOT) if os.path.isabs(path) else path
            zone, module = classify(rel, cfg)
            owner = owner_of(cfg, zone, module, rel)
            decision, reason = decide(rel, cfg, me, phase, branch)
            print(f"{rel}\n  구역={zone} 모듈={module or '-'} 담당={owner or '공동'} → {decision}: {reason}")
        return 0

    if args.cmd == "classify":
        paths = args.paths or [x.strip() for x in sys.stdin if x.strip()]
        author = member_by_github(cfg, args.author) if args.author else None
        print(" ".join(classify_labels(paths, cfg, author)))
        return 0

    if args.cmd == "other-reviewer":
        author = member_by_github(cfg, args.author)
        others = [m.github for k, m in cfg.members.items() if k != author and not m.github.startswith("TODO")]
        print(",".join(others))
        return 0

    if args.cmd == "check-pr-body":
        body = sys.stdin.read()
        problems = check_pr_body(body)
        if args.spec_changed:
            problems += check_code_sync(body)
        for prob in problems:
            print(f"✖ PR 본문: {prob}", file=sys.stderr)
        if problems:
            print("  → 작성자 세션에서 pr-write 스킬로 본문을 채우면 됨", file=sys.stderr)
            return 1
        print("✔ PR 본문 필수 섹션 확인")
        return 0

    if args.cmd == "spec-impact":
        impact = spec_impact(cfg, args.base)
        if not impact:
            print(f"api.md 변경 없음 ({args.base}...HEAD)")
        for line in impact:
            print(line)
        return 0

    if args.cmd == "check-pr-title":
        problems = check_pr_title(sys.stdin.read())
        for prob in problems:
            print(f"✖ PR 제목: {prob}", file=sys.stderr)
        if problems:
            print("  → GitHub PR 화면에서 제목만 고치면 검사가 다시 돎", file=sys.stderr)
            return 1
        print("✔ PR 제목 규칙 확인")
        return 0

    if args.cmd == "frozen-migrations":
        # 점 세 개: 갈라진 지점부터 이 PR이 바꾼 것만. 점 두 개면 develop에 새로 머지된 마이그레이션이 'D'로 잡혀 잘못 실패함 (#16)
        _, out = git("diff", "--name-status", "--no-renames", f"{args.base}...HEAD", "--", MIGRATION_DIR)
        bad = [line for line in out.splitlines() if line[:1] in ("M", "D")]
        for line in bad:
            print(f"✖ 이미 머지된 마이그레이션을 수정/삭제함: {line}", file=sys.stderr)
        if bad:
            print("  → 되돌리고 scripts/new-migration.sh 로 새 파일을 만들 것", file=sys.stderr)
            return 1
        print("✔ 기존 마이그레이션 변경 없음")
        return 0

    if args.cmd == "changed-modules":
        modules = set()
        for p in working_changed_paths():
            zone, module = classify(p, cfg)
            if module:
                modules.add(module)
        print(" ".join(sorted(modules)))
        return 0

    if args.cmd == "adr-hint":
        # 구조를 바꾸는 파일을 고쳤는데 결정 기록(ADR)이 없으면 needs-adr 출력 (CI 경고용, 차단 아님)
        paths = [x.strip() for x in sys.stdin if x.strip()]
        triggers = [p for p in paths if re.match(r"^(build\.gradle|settings\.gradle|gradle/libs\.versions\.toml|\.harness/zones\.conf)", p)]
        has_adr = any(p.startswith("docs/decisions/") for p in paths)
        if triggers and not has_adr:
            print("needs-adr")
        return 0

    if args.cmd == "new-migration":
        print(new_migration(cfg, args.module, args.desc))
        return 0
    return 0


if __name__ == "__main__":
    sys.exit(main())
