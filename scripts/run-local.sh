#!/usr/bin/env bash
# 로컬 실행. worktree마다 DB 이름과 포트를 자동으로 다르게 잡아서 서로 부딪히지 않게 한다.
#   scripts/run-local.sh          # 앱 실행 (bootRun, local 프로필, Docker 로컬 DB)
#   scripts/run-local.sh --shared # 공용 개발 DB(Supabase 개발 프로젝트)에 붙어 실행. 마이그레이션은 안 함 (ADR 0017)
#   scripts/run-local.sh --reset  # 이 worktree의 로컬 DB를 지우고 새로 만든 뒤 실행
#   scripts/run-local.sh --info   # 이 worktree가 쓰는 DB/포트만 출력
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

slug="$(basename "$PWD" | tr '[:upper:]' '[:lower:]' | tr -c 'a-z0-9\n' '_' | cut -c1-40)"
DB_NAME="capsule_${slug}"
# 디렉터리 이름 해시로 8080~8179 사이 포트 결정 (worktree마다 다름, 매번 같음)
PORT=$(( 8080 + $(printf '%s' "$PWD" | cksum | cut -d' ' -f1) % 100 ))

if [ "${1:-}" = "--info" ]; then
  echo "DB_NAME=$DB_NAME  SERVER_PORT=$PORT"
  exit 0
fi

# .env.local에서 정해 둔 이름의 줄만 읽는다(다른 값은 건드리지 않음). 이미 환경변수에 있으면 그 값을 쓴다
# 이 worktree에 .env.local이 없으면 원래 레포(메인 체크아웃)의 .env.local을 쓴다: worktree마다 복사하지 않게
# 첫 '='에서만 나눈다. 값 안이나 끝의 '='(base64 비밀번호 등)를 지키려고 IFS='=' read를 쓰지 않음
ENV_LOCAL=.env.local
if [ ! -f "$ENV_LOCAL" ]; then
  main_root="$(cd "$(git rev-parse --git-common-dir)/.." && pwd)"
  ENV_LOCAL="$main_root/.env.local"
fi
load_env_local() {
  [ -f "$ENV_LOCAL" ] || return 0
  while IFS= read -r line; do
    key="${line%%=*}"
    value="${line#*=}"
    if [ -z "${!key:-}" ]; then export "$key=$value"; fi
  done < <(tr -d '\r' < "$ENV_LOCAL" | grep -E "^($1)=" || true)
}
# LLM 공급자 · 키 (ADR 0022). 키는 .env.local(gitignore)에만, 레포 · 채팅에 남기지 않는다. 없으면 fake
load_env_local 'CAPSULE_LLM_PROVIDER|CAPSULE_LLM_FALLBACK_PROVIDER|CAPSULE_LLM_DECIDE_PROVIDER|ANTHROPIC_API_KEY|OPENAI_API_KEY|JEV_API_KEY|CAPSULE_LLM_LAB_TOKEN'

if [ "${1:-}" = "--shared" ]; then
  # shared-db 프로필 파일이 없는 옛 브랜치면 프로필 없이 local로 떠서 Docker 기본 DB에 마이그레이션해 버린다. 막는다
  if [ ! -f src/main/resources/application-shared-db.yml ]; then
    echo "✖ application-shared-db.yml 이 없음. git rebase origin/develop 후 다시 (ADR 0017)" >&2
    exit 1
  fi
  load_env_local 'CAPSULE_DEV_DB_URL|CAPSULE_DEV_DB_USERNAME|CAPSULE_DEV_DB_PASSWORD'
  for key in CAPSULE_DEV_DB_URL CAPSULE_DEV_DB_USERNAME CAPSULE_DEV_DB_PASSWORD; do
    if [ -z "${!key:-}" ]; then
      echo "✖ $key 가 비어 있음. .env.local에 공용 개발 DB 값을 넣는다 (.env.example 참고, ADR 0017)" >&2
      exit 1
    fi
  done
  docker compose up -d redis >/dev/null
  echo "▶ http://localhost:$PORT  (DB: 공용 개발 DB, 마이그레이션 안 함)"
  echo "ℹ 내 브랜치의 새 마이그레이션은 develop에 머지돼야 공용 DB에 들어감. 그 전엔 옵션 없이 실행(Docker 로컬 DB)"
  # 셸 · IDE에 남은 값이 shared-db 프로필을 덮지 않게
  unset SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD SPRING_FLYWAY_ENABLED
  export SPRING_PROFILES_ACTIVE=local,shared-db
  export SERVER_PORT="$PORT"
  exec ./gradlew bootRun
fi

docker compose up -d postgres redis >/dev/null
for _ in $(seq 1 30); do
  docker compose exec -T postgres pg_isready -U capsule >/dev/null 2>&1 && break
  sleep 1
done

if [ "${1:-}" = "--reset" ]; then
  docker compose exec -T postgres psql -U capsule -d postgres -c "DROP DATABASE IF EXISTS \"$DB_NAME\";" >/dev/null
fi
exists="$(docker compose exec -T postgres psql -U capsule -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'")"
if [ "$exists" != "1" ]; then
  docker compose exec -T postgres psql -U capsule -d postgres -c "CREATE DATABASE \"$DB_NAME\";" >/dev/null
  echo "ℹ 로컬 DB 생성: $DB_NAME"
fi

echo "▶ http://localhost:$PORT  (DB: $DB_NAME, LLM: ${CAPSULE_LLM_PROVIDER:-fake})"
export SPRING_PROFILES_ACTIVE=local
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/$DB_NAME"
export SERVER_PORT="$PORT"
exec ./gradlew bootRun
