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

## Troubleshooting

**Port already in use** — another process is on 8080, 8091, or 5433. Either stop it or change the port mapping in `infrastructure/docker-compose.yml`.

**`gradlew: Permission denied` (macOS/Linux)** — run `chmod +x gradlew` once.

**Keycloak fails to start** — it depends on the app DB being healthy. Run `make logs` to inspect, or wait 30 seconds and retry `make up`.

**Tests fail with "connection refused" on port 5434** — start the test DB: `docker compose -f infrastructure/docker-compose.yml up -d test-db`.

**Windows line endings break shell scripts inside Docker** — ensure your git config has `core.autocrlf=false` for this repo, or run `git config core.autocrlf false && git checkout .`.
