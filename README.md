# hanmaum-dn-server

Spring Boot 3 backend for the Hanmaum DN platform. Serves the Angular web dashboard and the Kotlin Multiplatform mobile app.

**Stack:** Kotlin 2.x · Spring Boot 3.x · PostgreSQL 15 · Keycloak 26 · Flyway · Docker Compose

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Docker Desktop | 4.x+ | Includes Docker Compose v2 |
| JDK | 21 | Only needed for running locally without Docker |
| Node.js | 20+ | Only needed for git hooks (`npm install`) |
| `make` | any | macOS: built-in · Windows: install via [Chocolatey](https://chocolatey.org/) or [GnuWin32](https://gnuwin32.sourceforge.net/packages/make.htm) |

**Windows users:** all commands below assume PowerShell or Git Bash. The `gradlew.bat` wrapper is used instead of `./gradlew`.

---

## 1. Clone and set up environment

```bash
git clone <repo-url>
cd hanmaum-dn-server
```

Install git hooks (required once per machine):

```bash
npm install
```

Create your local environment file from the example:

```bash
# macOS / Linux / Git Bash
cp .env.example .env

# PowerShell
Copy-Item .env.example .env
```

Open `.env` and fill in the required values (see comments inside the file). Never commit `.env`.
Generate a unique local Keycloak backend client secret, for example:

```bash
openssl rand -hex 32
```

Store the result as `KEYCLOAK_BACKEND_CLIENT_SECRET` in `.env`. The same
environment value is injected into the local Keycloak realm import and used by
the backend service account.

---

## 2. Start the full stack with Docker

This single command starts PostgreSQL, Keycloak, and the backend application:

```bash
# macOS / Linux
make up

# Windows (PowerShell, if make is not installed)
docker compose -p infrastructure --project-directory . -f infrastructure/docker-compose.yml up -d
```

Services and their ports after startup:

| Service | URL |
|---|---|
| Backend API | http://localhost:8080 |
| Keycloak Admin Console | http://localhost:8091 |
| PostgreSQL (app DB) | `localhost:5433` |
| PostgreSQL (test DB) | `localhost:5434` |

Keycloak admin credentials default to `admin / admin` (set `KEYCLOAK_ADMIN_USER` and `KEYCLOAK_ADMIN_PASSWORD` in `.env` to override).

---

## 3. Run the backend locally (without Docker)

Use this when you want fast iteration without rebuilding the Docker image. The database and Keycloak still run in Docker.

Start only the infrastructure services:

```bash
# macOS / Linux
docker compose -p infrastructure --project-directory . -f infrastructure/docker-compose.yml up -d hanmaumApp-db hanmaumApp-keycloak

# Windows (PowerShell)
docker compose -p infrastructure --project-directory . -f infrastructure/docker-compose.yml up -d hanmaumApp-db hanmaumApp-keycloak
```

Then run the Spring Boot app:

```bash
# macOS / Linux
./gradlew bootRun

# Windows
gradlew.bat bootRun
```

The app starts on **http://localhost:8080** with the `dev` Spring profile active.

### Optional: readable PII in the local database

PII is encrypted locally by default so development follows the production
storage path. For temporary database debugging, add this opt-in to your local
`.env`:

```env
PII_LOCAL_PLAINTEXT_ENABLED=true
```

The backend accepts this setting only with the `dev` or `local` profile and a
local PostgreSQL host. Startup fails if it is enabled for staging, production,
or a remote database. Existing encrypted rows remain readable; new and updated
PII is stored as plaintext. After removing the setting and restarting, the
normal startup backfill encrypts plaintext rows again.

---

## 4. Run tests

```bash
# macOS / Linux
./gradlew test

# Windows
gradlew.bat test
```

Integration tests require the test database container (`test-db` on port `5434`) to be running.

---

## 5. Useful make targets

| Command | What it does |
|---|---|
| `make up` | Start all services in the background |
| `make down` | Stop all services |
| `make build-backend` | Rebuild and restart only the backend container |
| `make logs` | Tail logs from all services |
| `make logs-backend` | Tail backend logs only |
| `make reset` | **Destructive.** Wipe all volumes and restart fresh. Use when DB init scripts change. |

---

## 6. First-time Keycloak setup

On a fresh volume Keycloak auto-imports the realm from `infrastructure/docker/keycloak/export/`. No manual setup is needed.

Realm imports do not update a realm that already exists. If
`KEYCLOAK_BACKEND_CLIENT_SECRET` changes, either rotate the
`dn-backend-admin` client secret in the Keycloak Admin Console and restart the
backend, or run `make reset` for a disposable local environment.

For staging and production, rotate this client secret independently in each
Keycloak instance, update the corresponding server environment, and restart
the backend. Never reuse the local value or commit a generated secret.

If the import fails or you see auth errors, check Keycloak is healthy:

```bash
docker inspect --format='{{.State.Health.Status}}' hanmaumApp-keycloak
```

---

## 7. Build a production JAR

```bash
# macOS / Linux
./gradlew bootJar

# Windows
gradlew.bat bootJar
```

Output: `build/libs/hanmaum-dn-server-*.jar`

The Dockerfile uses this same approach in its build stage — the Docker image is self-contained and does not require a JDK on the host at runtime.

---

## 8. Automated releases

Successful automatic staging deployments publish `ST-Release vX.Y.Z-st.N`
prereleases. Successful approved production deployments publish
`PROD-Release vX.Y.Z` as the latest stable release. The first release line is
`v0.1.0`; later versions are calculated from Conventional Commits:

- `type(scope)!:` or `BREAKING CHANGE:` increments `X` (major).
- `feat(scope):` increments `Y` (minor).
- `fix(scope):`, `perf(scope):`, or other deployable changes increment `Z` (patch).

GitHub-generated notes are grouped using the labels configured in
`.github/release.yml`. PR labels are derived from Conventional Commit titles.
Apply `release:security` manually when relevant or `release:skip` to omit a PR.
The immutable `release-notes-baseline` tag anchors the first changelog at the
repository root and is not a release version.
Published GitHub Releases are the authoritative release history;
`CHANGELOG.md` is not modified by CI.

---

## Troubleshooting

**Port already in use** — another process is on 8080, 8091, or 5433. Either stop it or change the port mapping in `infrastructure/docker-compose.yml`.

**`gradlew: Permission denied` (macOS/Linux)** — run `chmod +x gradlew` once.

**Keycloak fails to start** — it depends on the app DB being healthy. Run `make logs` to inspect, or wait 30 seconds and retry `make up`.

**Tests fail with "connection refused" on port 5434** — start the test DB: `docker compose -f infrastructure/docker-compose.yml up -d test-db`.

**Windows line endings break shell scripts inside Docker** — ensure your git config has `core.autocrlf=false` for this repo, or run `git config core.autocrlf false && git checkout .`.
