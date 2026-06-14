#!/usr/bin/env sh
set -eu

if [ "${BACKUP_ENABLED:-false}" != "true" ]; then
  echo "Backup is disabled (BACKUP_ENABLED is not true)." >&2
  exit 1
fi

: "${RESTIC_REPOSITORY:?RESTIC_REPOSITORY is required}"
: "${RESTIC_PASSWORD_FILE:?RESTIC_PASSWORD_FILE is required}"
: "${POSTGRES_CONTAINER:?POSTGRES_CONTAINER is required}"
: "${POSTGRES_USER:?POSTGRES_USER is required}"
: "${APP_DATABASE:?APP_DATABASE is required}"
: "${KEYCLOAK_DATABASE:?KEYCLOAK_DATABASE is required}"

retention_days="${BACKUP_RETENTION_DAYS:-30}"
work_dir="${BACKUP_WORK_DIR:-/var/backups/hanmaum-dn}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
run_dir="$work_dir/$timestamp"

umask 077
mkdir -p "$run_dir"
cleanup() {
  rm -rf "$run_dir"
}
trap cleanup EXIT INT TERM

dump_database() {
  database="$1"
  output="$run_dir/$database.dump"
  docker exec "$POSTGRES_CONTAINER" \
    pg_dump --format=custom --no-owner --no-acl \
    --username "$POSTGRES_USER" --dbname "$database" > "$output"
  test -s "$output"
}

dump_database "$APP_DATABASE"
dump_database "$KEYCLOAK_DATABASE"

if ! restic snapshots >/dev/null 2>&1; then
  restic init
fi

restic backup "$run_dir" \
  --tag hanmaum-dn \
  --host "$(hostname)" \
  --json
restic check --read-data-subset=5%
restic forget --keep-within "${retention_days}d" --prune

echo "Encrypted offsite backup completed at $timestamp."
