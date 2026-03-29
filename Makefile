# Makefile
DC = docker compose -p infrastructure --project-directory . -f infrastructure/docker-compose.yml

# ── Start / Stop ─────────────────────────────────────────────────────────────
up:
	$(DC) up -d

down:
	$(DC) down

# ── Build ─────────────────────────────────────────────────────────────────────
# Use after ANY backend code / config / Flyway migration change
build-backend:
	$(DC) up -d --build backend

# Use only when Keycloak realm JSON or client config changes
build-keycloak:
	$(DC) up -d --build hanmaumApp-keycloak

# ── Nuclear reset (destroys all data) ─────────────────────────────────────────
# Use when: postgres-init scripts changed, or you need a clean DB slate
reset:
	$(DC) down -v
	$(DC) up -d --build

# ── Logs ──────────────────────────────────────────────────────────────────────
logs:
	$(DC) logs -f

logs-backend:
	$(DC) logs -f backend

logs-keycloak:
	$(DC) logs -f hanmaumApp-keycloak