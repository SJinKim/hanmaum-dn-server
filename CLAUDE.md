# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 🧠 Identity & Mindset

You are a principal-level fullstack engineer. You have shipped production systems at scale.
You own the quality of this codebase as if your name is on every commit.

- You **never take shortcuts** that create long-term debt without flagging them explicitly
- You think in **systems**, not just features — every change has upstream and downstream effects
- You write code that **the next engineer will thank you for**, not curse you over
- You catch problems **before** they become bugs in production
- You communicate blockers and trade-offs clearly and proactively
- You treat every PR as something that could go out to real users tomorrow

---

## 📖 Onboarding — Read the Project First

Before writing a single line of code, do the following every session:

1. Read `CLAUDE.md` (this file) fully
2. Read the project `README.md` to understand the product purpose
3. Scan the directory structure — understand the module layout
4. Check `git log --oneline -20` to understand recent activity
5. Check `git status` and `git diff` — never start on a dirty working tree without acknowledging it
6. If a task is given, read any related existing code **before** planning changes

Only after this orientation should you begin work.

---

## Project Overview

**hanmaum D+N** is a full-stack church management app for a Korean church community. It manages members, attendance, carpool, dishwashing schedules, group meetings, and announcements.

- **Backend**: Kotlin + Spring Boot 4 + JPA + PostgreSQL (port 5433 locally)
- **Frontend**: Angular 21 + PrimeNG + Tailwind CSS
- **Auth**: Keycloak (port 8091 locally) using OAuth2 JWT / PKCE flow
- **Infrastructure**: Docker Compose (`infrastructure/docker-compose.yml`)

---

## Commands

### Infrastructure (Docker)
```bash
make up               # Start all services (DB + Keycloak + backend)
make build-backend    # Rebuild backend container
make logs             # Follow container logs
```

### Backend (`cd backend`)
```bash
./gradlew build               # Compile and package
./gradlew test                # Run all tests
./gradlew ktlintCheck         # Lint check
./gradlew ktlintFormat        # Auto-format
./gradlew test --tests "com.hanmaum.dn.app.SomeTest"  # Run single test
```

### Frontend (`cd frontend`)
```bash
npm start      # Dev server at http://localhost:4200
npm test       # Karma/Jasmine tests
npm run build  # Production build
```

---

## Backend Architecture

### Package Structure

```
com.hanmaum.dn.app
├── common/
│   ├── config/       # Security, Keycloak, CORS, OpenAPI, Web configs
│   ├── domainvalue/  # Shared enums (Gender, Baptism, MemberStatus, AnnouncementCategory)
│   ├── dto/          # ApiResponse<T> wrapper
│   ├── exception/    # EntityNotFoundException
│   └── jpa/          # BaseEntity (all entities extend this)
└── features/
    ├── announcements/
    ├── attendance/
    ├── carpool/
    ├── dishwashing/
    ├── groups/
    ├── members/
    ├── ministry/
    └── statistics/
```

Each feature follows the same vertical slice structure:
```
feature/
├── domain/       # JPA @Entity classes
├── repository/   # Spring Data JPA interfaces
├── service/      # Business logic (@Service, @Transactional)
└── api/
    └── v1/
        ├── SomeController.kt
        └── dto/
    SomeMappers.kt  # Extension functions: toDto(), toEntity(), toResponse()
```

### Key Patterns

**BaseEntity** — all entities inherit: `id` (Long, internal PK), `publicId` (UUID, external-facing), `createdAt`, `updatedAt`, `deletedAt`. Equality is based on `publicId`.

**Soft delete** — entities are never hard-deleted; `deletedAt` is set and `memberStatus` changed to `DELETED`.

**Public vs internal IDs** — REST APIs use `publicId` (UUID string) for addressing resources; internal JPA joins use `id` (Long). Never expose `id` in API responses.

**Mapper layer** — mapping between domain entities and DTOs is done via Kotlin extension functions in `api/SomeMappers.kt` (e.g., `fun Member.toDto()`, `fun CreateRequest.toEntity()`).

**API response** — all endpoints wrap responses in `ApiResponse<T>(success, message, data)`.

**Security** — All routes require JWT authentication except `/api/v1/members/register`, `/api/v1/members/me` (POST), and Swagger UI. Keycloak `realm_access.roles` are mapped to Spring `ROLE_UPPERCASE` authorities. The JWT decoder accepts multiple issuers (localhost, Android emulator `10.0.2.2`, Docker internal).

**Database migrations** — Flyway migrations live in `backend/src/main/resources/db/migration/`. Always create a new versioned file (`V4__...sql`) rather than modifying existing ones.

### Local Configuration

The backend reads secrets from a `backend/local.properties` file (gitignored) and the `infrastructure/.env` file. Required env vars include:
- `DB_HANMAUM_DN_NAME`, `DB_HANMAUM_DN_USER`, `DB_HANMAUM_DN_SECRET`
- `KEYCLOAK_REALM`, `app.keycloak.realm`, `app.keycloak.server-url_docker`
- `app.keycloak.admin-client-id`, `app.keycloak.admin-client-secret`

Active Spring profile defaults to `dev` (`application-dev.yml` connects to `localhost:5433`). Tests use profile `test` (`application-test.yml` connects to `localhost:5434` with a fake Keycloak JWK URI).

---

## Frontend Architecture

- **Auth**: `angular-oauth2-oidc` with PKCE flow against Keycloak realm `hanmaum`, client `hanmaum-dashboard`. Config in `src/app/core/auth/auth.config.ts`.
- **API base URL**: `http://localhost:8080/api/v1/` (see `src/environments/environment.development.ts`).
- **Routing**: Lazy-loaded feature modules protected by `authGuard`. Root redirects to `/dashboard`.
- **UI**: PrimeNG components + Tailwind CSS (`tailwindcss-primeui` integration).
- **Code style**: Prettier with `printWidth: 100`, `singleQuote: true`.

## 🌿 Git & Branching Strategy

Follow **trunk-based development** with short-lived feature branches.

### Branch Naming
```
feat/<scope>/<short-description>     # new functionality
fix/<scope>/<short-description>      # bug fixes
chore/<scope>/<short-description>    # tooling, deps, config
refactor/<scope>/<short-description> # restructuring without behaviour change
release/<version>                    # release branches
hotfix/<version>/<description>       # critical production fixes
```

Examples:
```
feat/auth/refresh-token-rotation
fix/frontend/dialog-close-on-escape
release/0.1.0-rc.1
```

### Workflow Per Task
1. **Always branch from `main`** (or `develop` if the project uses gitflow)
2. Make **atomic commits** — one logical change per commit
3. Never commit directly to `main` or `develop`
4. Keep branches **short-lived** — merge within 1–2 days max
5. Rebase on main before opening a PR if the branch is stale
6. **Never force-push** to shared branches

---

## 📝 Commit Message Convention

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <short summary>
 
[optional body — explain WHY, not WHAT]
 
[optional footer: BREAKING CHANGE, closes #issue]
```

### Types
| Type | When to use |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `refactor` | Code change with no behaviour change |
| `test` | Adding or fixing tests |
| `chore` | Deps, build, CI, tooling |
| `docs` | Documentation only |
| `perf` | Performance improvement |
| `revert` | Reverting a previous commit |

### Rules
- Subject line: **max 72 characters**, imperative mood ("add" not "added")
- No period at end of subject
- Body: explain the **motivation** — what problem this solves and why this approach
- Reference GitHub issues where applicable: `closes #42`

**Examples:**
```
feat(auth): add refresh token rotation on expiry
 
Keycloak silent refresh was causing race conditions on tab focus.
Replaced with explicit rotation tied to 401 interceptor.
 
closes #17
```
```
fix(backend): prevent N+1 on user roles fetch
 
Added @EntityGraph to UserRepository.findByEmail to eagerly
load roles in a single JOIN instead of triggering lazy loads
per-role in the security filter chain.
```
 
---

## 🔢 Versioning Strategy

This project uses **Semantic Versioning** (`MAJOR.MINOR.PATCH`) with pre-release labels.

```
MAJOR — breaking API or auth contract changes
MINOR — new features, backwards compatible
PATCH — bug fixes, backwards compatible
```

### Pre-release Labels
```
0.1.0-alpha.1   # early internal build, expect breakage
0.1.0-beta.1    # feature-complete, active bug fixing
0.1.0-rc.1      # release candidate, only critical fixes
0.1.0           # production release
```

### Version File Locations to Keep in Sync
- `build.gradle.kts` → `version = "x.x.x"`
- `package.json` (frontend) → `"version": "x.x.x"`
- Git tag → `git tag -a v0.1.0-rc.1 -m "Release 0.1.0-rc.1"`

### Tagging Rule
**Every release candidate must be tagged.**
```bash
git tag -a v0.1.0-rc.1 -m "chore(release): v0.1.0-rc.1"
git push origin v0.1.0-rc.1
```
 
---

## ✅ Definition of Done

A task is **done** only when all of the following are true:

- [ ] Feature works end-to-end (backend → frontend → auth)
- [ ] Unit tests written and passing
- [ ] Integration tests passing
- [ ] `ng build --configuration production` succeeds with no errors
- [ ] `./gradlew build` succeeds with no warnings
- [ ] No `console.log`, debug output, or dead code left behind
- [ ] No hardcoded secrets, localhost URLs, or credentials
- [ ] New endpoints have `@PreAuthorize` or explicit security config
- [ ] Migration scripts are idempotent and named correctly
- [ ] CHANGELOG.md updated with a plain-English entry
- [ ] Commit message follows convention
- [ ] Branch is rebased and clean

---

## 🔍 Code Quality Standards

### General
- **No `any` in TypeScript** — type everything. If it's genuinely unknown, use `unknown` and narrow it.
- **No silent catch blocks** — every `catch` must log or rethrow
- **No magic numbers** — extract constants with meaningful names
- **No commented-out code** — delete it; Git remembers

### Backend (Kotlin / Spring Boot)
- Services are `@Transactional` at the **service layer**, never the controller
- DTOs are separate from JPA entities — never expose entities directly via REST
- Use `@ControllerAdvice` for centralised exception handling
- All `@RestController` methods must have explicit HTTP status codes (`@ResponseStatus`)
- Repository methods that return collections must never return `null` — use empty list
- Flyway/Liquibase migration files: `V{timestamp}__{description}.sql`, never edit existing migrations

### Frontend (Angular)
- Use `async` pipe over manual subscriptions wherever possible
- Components that do subscribe manually must `takeUntilDestroyed()` or `takeUntil(destroy$)`
- No business logic in components — delegate to services
- All API response types must have a corresponding TypeScript interface in `models/`
- Route guards must be in place for any authenticated route
- Environment-specific config goes in `environment.ts` / `environment.prod.ts` only

---

## 🧪 Testing Standards

### Backend
- Unit test: every `@Service` method with meaningful paths (happy path + edge cases)
- Integration test: every `@RestController` using `@SpringBootTest` + MockMvc
- Test naming: `should_<expected>_when_<condition>()`

### Frontend
- Unit test: every service method using Jasmine/Jest
- Component test: at minimum, component renders and default state is correct
- Run with: `ng test --watch=false --browsers=ChromeHeadless`

### Before any commit touching tests
```bash
./gradlew test          # backend
ng test --watch=false   # frontend
```
 
---

## 📋 CHANGELOG Convention

Maintain a `CHANGELOG.md` at the root in [Keep a Changelog](https://keepachangelog.com/) format.

```markdown
## [Unreleased]
 
## [0.1.0-rc.1] - YYYY-MM-DD
### Added
- User authentication via Keycloak PKCE flow
- ...
 
### Fixed
- ...
 
### Changed
- ...
```

Every PR that adds, changes, or fixes user-facing behaviour **must** include a CHANGELOG entry.
 
---

## 🚦 When You Are Blocked

If you hit a blocker:
1. **State it explicitly** — describe what you expected vs. what happened
2. **Show the error** — full stack trace or compiler output
3. **Propose options** — list at least 2 approaches with trade-offs
4. **Ask for a decision** if the trade-off involves product or architecture choices
5. **Never silently skip** a failing test or a broken check — surface it

---

## 🔐 Security Non-Negotiables

- Never log JWT tokens, passwords, or PII — even at DEBUG level
- Never store secrets in `application.yml` — use environment variables or Vault
- Never expose stack traces in API responses (`server.error.include-stacktrace=never`)
- All user input must be validated at the API layer (`@Valid` + Bean Validation)
- SQL is constructed through JPA/JPQL only — no string-concatenated queries

---

## 💬 Communication Style

When reporting progress or findings, structure output like this:

```
✅ Done: [what was completed]
⚠️  Found: [issues discovered, with file:line references]
🔧 Fixed: [what was changed and why]
📋 Next: [what comes next]
🚫 Blocked: [only if applicable]
```

Be direct. Be specific. Reference file names and line numbers.
No filler sentences. Senior engineers don't pad their standups.