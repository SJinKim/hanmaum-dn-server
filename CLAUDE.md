# hanmaum-dn-server

Spring Boot 3 + Kotlin backend for the Hanmaum DN platform.
Serves the Angular web dashboard and the Kotlin Multiplatform mobile app.

## Stack
- Kotlin 2.x, Spring Boot 3.x, Gradle KTS
- PostgreSQL + Flyway migrations
- Keycloak for auth (JWT `sub` claim stored as `keycloak_id` in members)
- Docker Compose for local dev

## Current phase
Pre-MVP. See `.claude/skills/mvp-focus/` for scope rules.

## Non-negotiable rules
1. Plan before coding on any change touching 3+ files. Use `/plan` or Shift+Tab×2.
2. No silent lint suppression. If a rule must be disabled, justify inline.
3. No implicit `var` in Kotlin. Prefer `val`; `var` only when mutation is genuine.
4. Every `@Value` has a fallback default: `@Value("\${app.foo:defaultValue}")`.
5. Explicit port mapping in Docker. Never rely on dynamic ports.
6. Conventional Commits via `/commit`. Branch pattern: `<type>/HDN-<id>-<slug>`.
7. Never push directly to `main`. Feature branches only.
8. No secrets in code, commits, or Claude conversations. Use `.env` + Spring profiles.
9. Tests: unit tests for services, integration tests for controllers. No merge without a
   meaningful assertion — a passing test that asserts nothing is worse than no test.
10. Logging: SLF4J, structured kv pairs (`log.info("Provisioned member kcId={}", sub)`).
    No PII, no tokens, no full JWT contents — ever.
11. Errors: never swallow. Domain errors → `ProblemDetail` response. Infra errors →
    wrap with context and re-throw. No generic `catch(Exception)`.
12. Keycloak realm roles, groups and role mappings must be provisioned for existing realms
    by `scripts/keycloak/configure-realm.sh` or declarative input it consumes, in the same
    change as both realm exports. Export-only changes are incomplete. Record rollout
    separately from merge; verify ST first and never claim live configuration from Git alone.

## Layout
- `src/main/kotlin/.../controller` — REST endpoints (thin)
- `src/main/kotlin/.../service` — business logic
- `src/main/kotlin/.../repository` — Spring Data JPA
- `src/main/kotlin/.../domain` — entities, value objects
- `src/main/kotlin/.../config` — Spring config, security
- `src/main/resources/db/migration` — Flyway SQL

## Cross-repo references
- API contract: `hanmaum-dn-ops/api/openapi.yaml` (source of truth)
- OpenAPI sync: `OPS_DIR=/absolute/path/to/hanmaum-dn-ops OPENAPI_ENV_FILE=/absolute/path/to/.env make openapi-sync`.
  This starts a disposable PostgreSQL on localhost:15433, generates from this checkout on port 8089,
  copies into the specified ops Git checkout, and removes the database container afterward.
  Run this command after every server API contract change, review the generated ops diff, and
  link the ops PR from the server PR. With sibling checkouts and a local `.env`, `make openapi-sync`
  uses those defaults. A server commit does not trigger the command automatically.
  If a disposable database is already running, invoke Gradle directly with
  `./gradlew syncOpenApiToOps -PopsDir=/absolute/path/to/hanmaum-dn-ops -PopenApiEnvFile=/absolute/path/to/.env -PopenApiDbPort=15433`.
- Definition of Done: `hanmaum-dn-ops/DEFINITION_OF_DONE.md`
- Sprint notes: `hanmaum-dn-ops/sprints/`

## Custom commands (`.claude/commands/`)
- `/plan` — plan mode with backend context loaded
- `/commit` — staged changes → Conventional Commit
- `/pr` — open PR from current branch using our template
- `/review` — self-review diff before opening PR
- `/sprint-status` — current milestone snapshot

## Skills (`.claude/skills/`, loaded on demand)
- `auth-keycloak` — JWT, `keycloak_id` linking, first-login member provisioning
- `db-migrations` — Flyway conventions, safe migration patterns
- `api-contracts` — reading/updating the shared OpenAPI spec
- `mvp-focus` — MVP scope rules (retired when v1.0.0 ships)

## Workflow Orchestration

### Plan Mode
- Rule 1 already requires planning for 3+ file changes — also enter plan mode for architectural decisions and verification steps.
- If something goes sideways: STOP immediately, re-plan. Don't push through a broken approach.

### Subagent Strategy
Delegate only when it *shrinks* the main context. The test: would the work generate a lot
of tool output I don't need to keep?

- **Delegate**: executing one task of a written plan (the plan is the subagent's context,
  so it starts warm); broad searches where only the conclusion matters; anything whose
  verification is a command exit code rather than a judgment call.
- **Do inline**: design decisions, anything touching shared infrastructure (the local
  Postgres volume is shared across worktrees), and work where I'd have to read the whole
  diff to trust it — then I've paid for the context twice.
- One task per subagent, and run them **sequentially**. See the token rules below.

### Self-Improvement Loop
- After ANY user correction: append the pattern to `tasks/lessons.md`.
- Write a rule that prevents the same mistake from recurring.
- Review `tasks/lessons.md` at session start.

### Verification Gate
- Never mark a task complete without proving it works.
- Ask: "Would a staff engineer approve this PR?"
- Run tests, check logs, demonstrate correctness before marking Done.

### Bug Fixing
- When given a bug report: just fix it — no hand-holding requests.
- Point at logs, errors, failing tests and resolve autonomously.

### Core Principles
- Simplicity First: minimal code impact per change.
- No Laziness: find root causes, no temporary fixes.
- Minimal Impact: only touch what's necessary.

## Token-efficient workflow (Pro plan)
- `/clear` between unrelated tasks.
- `/compact` at ~50% context.
- Reference files as `@path/to/File.kt`, not "look at the codebase".
- No Agent Teams / parallel sub-agent fan-out on Pro — several agents starting cold on the
  same problem re-derive the same context and multiply tokens 3–7×.
- Sequential subagents are the exception and are often *cheaper* than working inline: the
  subagent's file reads and tool output stay in its context and are discarded, while inline
  reads compound in the main context and are re-sent every turn. Use one per task, only for
  tasks specified well enough that verification is running a command, not reviewing a design.

## Concurrent agent coordination

When Codex, Claude, or another session works on this repository at the same time:

1. Before choosing an issue or editing code, resolve the shared coordination directory with
   `git rev-parse --path-format=absolute --git-common-dir` plus `/agent-coordination`.
   Read `codex.md`, `claude.md`, active issue claims and current GitHub PRs. A worktree
   isolates files but does not prevent two agents from implementing the same issue.
2. Claim the issue atomically before coding. Create the parent `issues/` directory with
   `mkdir -p`, then use plain `mkdir issues/<issue-number>` inside the coordination directory
   (never `mkdir -p` for the individual claim). If the claim already exists, read its owner
   and coordinate instead of overwriting it. Record owner/session, UTC timestamp, branch,
   worktree, scope and PR in its `owner.md` and in your own status file. Claims remain owned
   through review; stale claims require checking the owner, worktree and GitHub state before
   an explicit takeover. Never infer a handoff merely from silence.
3. Use a separate worktree and feature branch per issue. Do not change another session's
   checkout or uncommitted files. Coordinate shared databases, Docker containers, Keycloak,
   OpenAPI generation and ports before use; disposable test infrastructure must have its own
   name and explicit port. Read the peer's status again before taking the next issue.
4. Request reciprocal reviews when both sessions are reachable. Record the exact reviewed
   commit SHA, findings and tests; after a head change, review the new diff before carrying
   findings forward. If both agents use the same GitHub account, post COMMENT reviews and
   do not claim that these satisfy an approving-review requirement. An unavailable peer is
   a pending review, not an implicit approval.
5. Update your own status at implementation, test, PR, review and handoff checkpoints.
   Release only your claim after the PR is merged/closed or an explicit handoff. Keep code
   completion, merge, deployment and verified live configuration separate. This protocol
   does not authorize automatic merges, deployments or cross-repository implementation.
6. When new product or architecture decisions require the user, collect the unresolved
   choices in the final report with affected issues/PRs, options and a recommendation.
   Keep affected PRs open and continue independent work. Do not infer approval or revisit
   decisions the user has already approved. Do not merge PRs during this issue queue.
