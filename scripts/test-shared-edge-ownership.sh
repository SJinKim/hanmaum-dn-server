#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
workflows=(
  "$ROOT/.github/workflows/deploy-prod.yml"
  "$ROOT/.github/workflows/deploy-staging.yml"
)

for workflow in "${workflows[@]}"; do
  grep -q 'docker network inspect caddy-proxy' "$workflow"
  grep -q "docker inspect --format='{{.State.Running}}' hanmaum-caddy" "$workflow"
  grep -q 'graceops.edge.owner' "$workflow"

  if grep -Eq 'docker compose.*hanmaum-caddy|docker-compose\.caddy\.yml|caddy reload|docker network create caddy-proxy' "$workflow"; then
    echo "Application workflow must not mutate the shared edge: $workflow" >&2
    exit 1
  fi
done

echo "Shared edge ownership contract passed."
