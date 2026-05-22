# Deployment Setup — hanmaum-dn-server

> Created: 2026-05-22
> Covers: production Docker setup, Caddy reverse proxy, GitHub Actions CI/CD pipelines

---

## Overview

The app is deployed on a **Hetzner CX33** (4 vCPU, 8 GB RAM, 160 GB NVMe).
- Only ports **80 and 443** are exposed publicly — everything else is on an internal Docker network.
- **Caddy** handles TLS automatically via Let's Encrypt. No certbot, no cron jobs.
- **GitHub Actions** builds the Docker image in CI, pushes to GHCR, then SSHes to the server to deploy.
- `main` is **never auto-deployed** on push — production only deploys via manual `workflow_dispatch`.

---

## Files Created / Modified

| File | What it is |
|---|---|
| `docker-compose.prod.yml` | Production Docker Compose (no test-db, no exposed ports, Caddy included) |
| `infrastructure/caddy/Caddyfile` | Caddy reverse proxy config (auto-HTTPS) |
| `Makefile` | Added `prod-*` targets |
| `.github/workflows/deploy-staging.yml` | Staging deploy pipeline |
| `.github/workflows/deploy-prod.yml` | Production deploy pipeline with approval gate |

---

## Architecture

```
Internet
    │
    ▼
Caddy :80/:443          ← only exposed ports
    ├── api.yourdomain.com   → backend:8080
    └── auth.yourdomain.com  → keycloak:8090
                                    │
                        [internal Docker network]
                                    │
                        postgres:5432   (never exposed)
```

All four services (db, keycloak, backend, caddy) run on a shared internal bridge network. No service is directly reachable from the internet except through Caddy.

---

## docker-compose.prod.yml — Key Differences vs Dev

| Concern | Dev (`infrastructure/docker-compose.yml`) | Prod (`docker-compose.prod.yml`) |
|---|---|---|
| Keycloak mode | `start-dev` | `start` (production-hardened) |
| Port exposure | 5433, 8080, 8091, 9001 | Only 80/443 via Caddy |
| TLS | None | Auto via Let's Encrypt |
| `test-db` service | Present | Removed |
| Network isolation | None | Internal bridge network |
| `KC_HOSTNAME_STRICT` | `false` | `true` (default in prod mode) |
| Proxy headers | Not set | `KC_PROXY_HEADERS: xforwarded` |
| Backend image | Built locally from `Dockerfile` | Pulled from GHCR (`${BACKEND_IMAGE}`) |
| Postgres data | Host-mounted path | Named Docker volume |

---

## Environment Variables

### Required in `.env` on the server (on top of existing dev vars)

```bash
# Caddy / TLS
ACME_EMAIL=you@example.com          # Let's Encrypt contact address
API_DOMAIN=api.yourdomain.com       # Public hostname for the backend API
AUTH_DOMAIN=auth.yourdomain.com     # Public hostname for Keycloak

# Set automatically by CI during deploy — do NOT set manually
# BACKEND_IMAGE=ghcr.io/sjinkim/hanmaum-dn-server:sha-<short>
```

### How `BACKEND_IMAGE` works

`docker-compose.prod.yml` declares:
```yaml
backend:
  image: ${BACKEND_IMAGE:-ghcr.io/sjinkim/hanmaum-dn-server:latest}
```

CI exports `BACKEND_IMAGE` as an env var before running `docker compose up`, so the server always runs the exact SHA-tagged image that CI built. Falls back to `:latest` if the variable is absent.

---

## Caddy (`infrastructure/caddy/Caddyfile`)

```
{
    email {$ACME_EMAIL}
}

{$API_DOMAIN} {
    reverse_proxy backend:8080
}

{$AUTH_DOMAIN} {
    reverse_proxy hanmaumApp-keycloak:8090
}
```

Caddy reads `ACME_EMAIL`, `API_DOMAIN`, and `AUTH_DOMAIN` from the container environment (passed via `docker-compose.prod.yml`).

TLS certificates are stored in the `caddy_data` named volume — **this volume must persist across restarts**. Never delete it or TLS certs will be lost.

---

## Keycloak Production Config

Keycloak runs with `start` (not `start-dev`). Key env vars changed from dev:

| Variable | Value | Reason |
|---|---|---|
| `KC_HTTP_ENABLED` | `true` | Caddy handles TLS; KC runs plain HTTP internally |
| `KC_HOSTNAME` | `https://${AUTH_DOMAIN}` | Correct public URL for redirect URIs |
| `KC_PROXY_HEADERS` | `xforwarded` | Trust `X-Forwarded-*` headers from Caddy |
| `KC_HOSTNAME_STRICT` | *(not set — defaults to `true`)* | Enforces hostname validation |

---

## Makefile Targets

```bash
# Dev (unchanged)
make up                  # start dev stack
make down                # stop dev stack
make build-backend       # rebuild backend (dev)
make reset               # nuke volumes and restart (dev)
make logs                # tail all logs (dev)

# Production
make prod-up             # start prod stack (pulls image from GHCR)
make prod-down           # stop prod stack
make prod-build          # build image locally and start (local testing only)
make prod-logs           # tail all logs (prod)
make prod-logs-backend   # tail backend logs (prod)
make prod-logs-caddy     # tail Caddy logs (prod)
make prod-logs-keycloak  # tail Keycloak logs (prod)
```

---

## GitHub Actions Pipelines

### Pipeline: `deploy-staging.yml`

**Triggers:** push to `dev` branch OR manual `workflow_dispatch`

```
[test]          ktlint + gradle test (postgres sidecar)
   │
[build]         docker build → push :staging + :sha-{7} to GHCR
   │
[deploy]        SSH → git pull dev
                     → docker compose pull backend
                     → docker compose up -d
                (environment: staging)
```

### Pipeline: `deploy-prod.yml`

**Trigger:** manual `workflow_dispatch` only — `main` branch only

```
[guard]         fails if ref ≠ refs/heads/main
   │
[build]         docker build → push :latest + :sha-{7} to GHCR
   │
[deploy]        ⏸ waits for required reviewer approval
                SSH → git pull main
                     → docker compose pull backend
                     → docker compose up -d
                (environment: production)
```

`cancel-in-progress: false` on prod — a running production deploy is never cancelled by a new trigger.

### Image Tagging Strategy

| Pipeline | Tags pushed |
|---|---|
| Staging | `:staging`, `:sha-{7char}` |
| Production | `:latest`, `:sha-{7char}` |

The deploy step always uses the deterministic `:sha-{7char}` tag, not `:latest` or `:staging`.

---

## One-Time Setup Checklist

### GitHub (do once per environment)

- [ ] Go to `Settings → Environments` and create two environments: `staging` and `production`
- [ ] On `production`: add yourself (or a teammate) as a **required reviewer**
- [ ] Add the following secrets to **each** environment:

| Secret | Description |
|---|---|
| `SERVER_HOST` | Server IP or hostname |
| `SERVER_USER` | SSH username (`ubuntu`, `deploy`, etc.) |
| `SERVER_SSH_KEY` | Ed25519 private key — no passphrase |
| `GHCR_TOKEN` | GitHub PAT with `read:packages` scope only |

To generate an Ed25519 SSH key pair:
```bash
ssh-keygen -t ed25519 -C "github-deploy" -f ~/.ssh/deploy_key -N ""
# Add deploy_key.pub to the server's ~/.ssh/authorized_keys
# Paste deploy_key contents into SERVER_SSH_KEY secret
```

To generate a GHCR pull token:
1. `GitHub → Settings → Developer settings → Personal access tokens → Fine-grained`
2. Permissions: `Packages: Read`
3. Paste into `GHCR_TOKEN` secret

### Server (do once per server)

```bash
# 1. Clone the repo
mkdir -p /opt/hanmaum-dn-server
git clone https://github.com/SJinKim/hanmaum-dn-server.git /opt/hanmaum-dn-server

# 2. Create and fill in the .env file
cp /opt/hanmaum-dn-server/.env.example /opt/hanmaum-dn-server/.env
nano /opt/hanmaum-dn-server/.env
# Fill in DB credentials, Keycloak admin creds, ACME_EMAIL, API_DOMAIN, AUTH_DOMAIN

# 3. Open firewall ports (Hetzner Cloud Firewall or ufw)
ufw allow 22    # SSH
ufw allow 80    # HTTP (Caddy ACME challenge)
ufw allow 443   # HTTPS

# 4. Point DNS before first start
# api.yourdomain.com  → server IP
# auth.yourdomain.com → server IP

# 5. First deploy (Caddy fetches TLS certs automatically)
cd /opt/hanmaum-dn-server
make prod-up
```

---

## Hetzner CX33 — Resource Budget

| Service | Expected RAM |
|---|---|
| Spring Boot backend | ~512 MB |
| Keycloak | ~512 MB |
| PostgreSQL | ~256 MB |
| Caddy | ~50 MB |
| **Total** | **~1.3 GB / 8 GB** |

CX33 has plenty of headroom. The JVM `-Xmx` is not capped in the current `Dockerfile` — consider adding `-Xmx512m` to `ENTRYPOINT` if memory pressure is ever observed.
