# Makefile
DC      = docker compose -p infrastructure --project-directory . -f infrastructure/docker-compose.yml
DC_PROD = docker compose -p hanmaum-prod   --project-directory . -f docker-compose.prod.yml

# ── Dev: Start / Stop ────────────────────────────────────────────────────────
up:
	$(DC) up -d

down:
	$(DC) down

# ── Dev: Build ────────────────────────────────────────────────────────────────
# Use after ANY backend code / config / Flyway migration change
build-backend:
	$(DC) up -d --build backend

# Use only when Keycloak realm JSON or client config changes
build-keycloak:
	$(DC) up -d --build hanmaumApp-keycloak

# ── Security: local PII keyring ──────────────────────────────────────────────
pii-keyring:
	./scripts/generate-pii-keyring.sh

# ── Production backup helpers (requires .env.backup + restic) ────────────────
backup-now:
	set -a; . ./.env.backup; set +a; ./infrastructure/backup/backup.sh

backup-restore-test:
	set -a; . ./.env.backup; set +a; ./infrastructure/backup/restore-test.sh

# ── Dev: Nuclear reset (destroys all data) ────────────────────────────────────
# Use when: postgres-init scripts changed, or you need a clean DB slate
reset:
	$(DC) down -v
	$(DC) up -d --build

# ── Dev: Logs ─────────────────────────────────────────────────────────────────
logs:
	$(DC) logs -f

logs-backend:
	$(DC) logs -f backend

logs-keycloak:
	$(DC) logs -f hanmaumApp-keycloak

# ── Prod: Start / Stop ────────────────────────────────────────────────────────
prod-up:
	$(DC_PROD) up -d

prod-down:
	$(DC_PROD) down

# ── Prod: Build (local image only — CI is the authoritative build path) ───────
prod-build:
	docker build -t ghcr.io/sjinkim/hanmaum-dn-server:local .
	BACKEND_IMAGE=ghcr.io/sjinkim/hanmaum-dn-server:local $(DC_PROD) up -d

# ── Prod: Logs ────────────────────────────────────────────────────────────────
prod-logs:
	$(DC_PROD) logs -f

prod-logs-backend:
	$(DC_PROD) logs -f backend

prod-logs-caddy:
	$(DC_PROD) logs -f caddy

prod-logs-keycloak:
	$(DC_PROD) logs -f hanmaumApp-keycloak
