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

if [ "${1:-}" = "--shared" ]; then
  # .env.local에서 공용 개발 DB 세 줄만 읽는다(다른 값은 건드리지 않음). 이미 환경변수에 있으면 그 값을 쓴다
  if [ -f .env.local ]; then
    while IFS='=' read -r key value; do
      if [ -z "${!key:-}" ]; then export "$key=$value"; fi
    done < <(tr -d '\r' < .env.local | grep -E '^CAPSULE_DEV_DB_(URL|USERNAME|PASSWORD)=' || true)
  fi
  for key in CAPSULE_DEV_DB_URL CAPSULE_DEV_DB_USERNAME CAPSULE_DEV_DB_PASSWORD; do
    if [ -z "${!key:-}" ]; then
      echo "✖ $key 가 비어 있음. .env.local에 공용 개발 DB 값을 넣는다 (.env.example 참고, ADR 0017)" >&2
      exit 1
    fi
  done
  docker compose up -d redis >/dev/null
  echo "▶ http://localhost:$PORT  (DB: 공용 개발 DB, 마이그레이션 안 함)"
  echo "ℹ 내 브랜치의 새 마이그레이션은 develop에 머지돼야 공용 DB에 들어감. 그 전엔 옵션 없이 실행(Docker 로컬 DB)"
  unset SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD
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

echo "▶ http://localhost:$PORT  (DB: $DB_NAME)"
export SPRING_PROFILES_ACTIVE=local
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/$DB_NAME"
export SERVER_PORT="$PORT"
exec ./gradlew bootRun
