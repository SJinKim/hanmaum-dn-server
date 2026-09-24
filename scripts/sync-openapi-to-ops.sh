#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ops_dir="${OPS_DIR:-$repo_root/../hanmaum-dn-ops}"
env_file="${OPENAPI_ENV_FILE:-$repo_root/.env}"
project_name="hdn-openapi-$$"
compose_file="$repo_root/infrastructure/docker-compose.openapi.yml"

if [[ ! -f "$env_file" ]]; then
    printf 'OpenAPI environment file not found: %s\n' "$env_file" >&2
    exit 1
fi

cleanup() {
    docker compose --project-name "$project_name" --env-file "$env_file" -f "$compose_file" down --volumes >/dev/null
}
trap cleanup EXIT

docker compose --project-name "$project_name" --env-file "$env_file" -f "$compose_file" up -d --wait

cd "$repo_root"
./gradlew syncOpenApiToOps \
    "-PopsDir=$ops_dir" \
    "-PopenApiEnvFile=$env_file" \
    -PopenApiDbPort=15433
