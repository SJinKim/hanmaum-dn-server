#!/usr/bin/env sh
set -eu

: "${RESTIC_REPOSITORY:?RESTIC_REPOSITORY is required}"
: "${RESTIC_PASSWORD_FILE:?RESTIC_PASSWORD_FILE is required}"
: "${POSTGRES_CONTAINER:?POSTGRES_CONTAINER is required}"
: "${POSTGRES_USER:?POSTGRES_USER is required}"
: "${APP_DATABASE:?APP_DATABASE is required}"
: "${KEYCLOAK_DATABASE:?KEYCLOAK_DATABASE is required}"
: "${RESTORE_DATABASE:?RESTORE_DATABASE must name an isolated restore database}"

case "$RESTORE_DATABASE" in
  "$APP_DATABASE"|"$KEYCLOAK_DATABASE")
    echo "Refusing to restore over an application or Keycloak database." >&2
    exit 1
    ;;
esac

snapshot="${1:-latest}"
source_database="${2:-$APP_DATABASE}"
restore_dir="$(mktemp -d)"
trap 'rm -rf "$restore_dir"' EXIT INT TERM

restic restore "$snapshot" --target "$restore_dir"
dump_file="$(find "$restore_dir" -name "$source_database.dump" -type f | head -n 1)"
test -n "$dump_file"

docker exec "$POSTGRES_CONTAINER" dropdb \
  --if-exists --username "$POSTGRES_USER" "$RESTORE_DATABASE"
docker exec "$POSTGRES_CONTAINER" createdb \
  --username "$POSTGRES_USER" "$RESTORE_DATABASE"
docker exec -i "$POSTGRES_CONTAINER" pg_restore \
  --exit-on-error --no-owner --no-acl \
  --username "$POSTGRES_USER" --dbname "$RESTORE_DATABASE" < "$dump_file"

echo "Restore test completed into isolated database '$RESTORE_DATABASE'."
