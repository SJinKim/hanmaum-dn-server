# /dev-session — Autonomous Development Session

Execute these steps in order. Do not skip steps.

---

## 0. Resolve Project Paths

The workspace root is `Private_Projects/`. Locate all three project directories:

```bash
# Find backend (look for the Spring Boot main class)
find /sessions/focused-determined-mccarthy/mnt -maxdepth 4 \
  -name "ChurchDnBackendApplication.kt" 2>/dev/null

# Find mobile app
find /sessions/focused-determined-mccarthy/mnt -maxdepth 3 \
  -type d -name "HanmaumDnApp" 2>/dev/null

# Find or note dashboard location
find /sessions/focused-determined-mccarthy/mnt -maxdepth 3 \
  -type d -name "dn-app-dashboard" 2>/dev/null
```

Set these as your working paths for the rest of the session:
- `$BACKEND` = parent of `backend/src/main/kotlin/`
- `$APP` = `HanmaumDnApp/` directory
- `$DASHBOARD` = `dn-app-dashboard/` directory (may not exist yet — see Step 4)

---

## 1. Load Context

Read in this order:
1. `$BACKEND/CLAUDE.md` — architecture rules (authoritative, non-negotiable)
2. `$BACKEND/MVP.md` — feature specs, API contracts, DoD, current status
3. `$BACKEND/dev-log.md` (if it exists) — previous session output; start where it left off

---

## 2. Reconcile Status

Scan all three codebases and compare against the MVP.md status table.
For each feature + layer, determine actual completion:

**Backend ✅ requires ALL of:**
- Entity extends `BaseEntity`, no internal `id` exposed in any DTO
- Repository interface
- Service with `@Transactional` on write methods, proper validation and business logic
- Controller with `ApiResponse<T>` wrapper on every endpoint
- Mapper as Kotlin extension functions in `api/{Feature}Mappers.kt`
- Flyway migration file (immutable — never touch existing ones)
- Input validation (`@Valid`, constraint annotations)
- Error handling: 404 not found, 409 duplicate, 400 bad state/outside-window, 403 unauthorized

**Dashboard ✅ requires ALL of:**
- Angular service wired to real backend endpoints (zero hardcoded data)
- List view with search and pagination
- Detail view and edit/create form
- All labels, statuses, options fetched from API

**App ✅ requires ALL of:**
- KMP screen(s) for the feature
- ViewModel/UseCase wired to real backend API
- Time-window and business rule validation enforced server-side; UI only reflects API response
- Works without needing direct DB access

Update MVP.md status table to match reality **before** starting any work.
If any status changed: commit on `dev` — `docs: reconcile MVP status with codebase`.

---

## 3. Select ONE Feature for This Session

**Rule: one feature per session.** Pick the single highest-priority incomplete feature and work on ALL its incomplete layers before touching anything else.

Priority order (from MVP.md build order):
1. F1: Member Management
2. F2: Ministry + Registration
3. F3: Attendance Tracking

Within the selected feature, work layers in order: **Backend → Dashboard → App**.
Backend must be ✅ before Dashboard or App work begins for the same feature.

If the previous dev-log.md says a feature was in progress, continue that one — do not switch.

---

## 4. Implement

### Branch strategy (per project repo)

```bash
cd $BACKEND    # or $DASHBOARD or $APP depending on layer
git checkout dev && git pull
git checkout -b feature/{kebab-feature-name}-{backend|dashboard|app}
```

### If Dashboard project does not exist

```bash
cd $(dirname $DASHBOARD)
npx @angular/cli@21 new dn-app-dashboard \
  --routing --style=scss --skip-tests=false --package-manager=npm
cd dn-app-dashboard
npm install primeng primeicons primeflex @primeng/themes
# Set up Tailwind CSS per Angular 21 docs
# Configure Keycloak OIDC (keycloak-js) for PKCE auth
# Create shared ApiService, AuthService, interceptor for JWT
git init && git checkout -b dev && git add . && git commit -m "chore: scaffold Angular 21 dashboard"
```

### Quality bar — no exceptions

- No `TODO`, no `// placeholder`, no stub methods that aren't fully implemented
- Every endpoint fully implemented: validation + error handling + correct HTTP status codes
- Backend: `@Transactional` on all service write methods
- Mappers: extension functions only — never inline mapping in controllers or services
- Flyway files: `V{System.currentTimeMillis()}__{snake_case_description}.sql`
- Dashboard/App: all text, status labels, enum values from API — nothing hardcoded in source

When in doubt about a field or behavior, refer to the DoD and API contract in MVP.md.

### Token budget rule

Monitor context usage. If approaching the context limit:
1. **Stop cleanly** — do not start a new sub-task you cannot finish
2. Commit all completed work
3. Write dev-log.md entry (see Step 6)
4. **Do not purchase or spend money for more tokens** — stop and wait for the next session

---

## 5. Verify Before Committing

**Backend:**
```bash
cd $BACKEND && ./gradlew build   # must pass with 0 errors
```
- All new endpoints secured with correct role (ADMIN vs MEMBER per MVP.md)
- No internal `id` in any response DTO
- Flyway scripts are syntactically valid SQL

**Dashboard:**
```bash
cd $DASHBOARD && ng build   # must produce 0 errors
```

**App:**
```bash
cd $APP && ./gradlew build   # or equivalent KMP build command
```

Fix any build failure before committing. Do not commit broken code.

---

## 6. Commit & Update Status

Run `/commit` in each repo where changes were made.

Then update `$BACKEND/MVP.md`:
- Completed layer: 🔄 → ✅
- Not-yet-started layer you worked on: 🔲 → 🔄
- Commit: `docs: update MVP status — {Feature} {Layer} done`

Create or update the feature's superpowers spec doc at `$BACKEND/docs/superpowers/specs/YYYY-MM-DD-{feature}-design.md`.
Document what was built: domain model, migration, endpoints, guards, files delivered.
Commit alongside code — do NOT write feature details into `CHANGELOG.md`.

---

## 7. Session Log

Append to `$BACKEND/dev-log.md` (create if missing):

```markdown
## {YYYY-MM-DD}
### Feature
- {F1|F2|F3}: {feature name}
### Completed
- {layer}: {what was finished — file:line references}
### In Progress
- {layer}: {what is mid-flight, if any}
### Next
- {exact next task for the following session}
### Blockers
- {what blocked progress, or "none"}
```

---

## Blocker Protocol

If Docker / PostgreSQL / Keycloak is unavailable:
1. Document under Blockers in dev-log.md
2. Work on a layer that doesn't need infra (e.g. Dashboard/App scaffolding, models, services without integration tests)
3. Do not spin-wait — move on immediately
