#!/usr/bin/env bash
# 마이그레이션 파일 생성기. 직접 파일을 만들지 말고 항상 이걸 쓴다.
#   scripts/new-migration.sh <모듈> <설명>
#   예) scripts/new-migration.sh session add_focus_event_table
# 결과: src/main/resources/db/migration/V20261103_143012__session_add_focus_event_table.sql
# - 버전이 타임스탬프라 둘이 동시에 만들어도 번호가 겹치지 않음
# - 파일명에 모듈이 들어가서 누구 테이블인지 하네스가 판정할 수 있음
set -euo pipefail
if [ $# -ne 2 ]; then
  echo "사용법: scripts/new-migration.sh <모듈> <설명_영문_snake>" >&2
  exit 1
fi
cd "$(git rev-parse --show-toplevel)"
python3 .harness/zones.py new-migration "$1" "$2"
